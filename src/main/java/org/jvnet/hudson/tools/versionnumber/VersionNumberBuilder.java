package org.jvnet.hudson.tools.versionnumber;

import hudson.Extension;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * This build wrapper makes an environment variable with a version number available
 * to the build.  For more information on how the format stream works, see the Version
 * Number Plugin wiki page.
 * </p>
 * <p>
 * This plugin keeps track of its version through a {@link VersionNumberAction} attached
 * to the project.  Each build that uses this plugin has its own VersionNumberAction,
 * and this contains the builds today / this week / this month / this year / all time.
 * When incrementing each of these values, unless they're overridden in the configuration
 * the value from the previous build will be used.
 * </p>
 * <p>
 * Such a value can be either overridden with a plain number or with an environment-variable.
 * In the later case the value will be read from the environment-variable at build-time. If
 * it cannot be parsed as an integer the value from the previous build will be incremented
 * and used instead.
 * </p>
 *
 * @author Carl Lischeske - NETFLIX
 * @author Deniz Bahadir - BENOCS
 */
public class VersionNumberBuilder extends BuildWrapper {
    
    private static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    // Pattern:   ${VAR_NAME} or $VAR_NAME
    private static final String ENV_VAR_PATTERN = "^(?:\\$\\{(\\w+)\\})|(?:\\$(\\w+))$";

    private final String versionNumberString;
    private final Date projectStartDate;
    private final String environmentVariableName;
    private final String environmentPrefixVariable;
    
    private String oBuildsToday;
    private String oBuildsThisWeek;
    private String oBuildsThisMonth;
    private String oBuildsThisYear;
    private String oBuildsAllTime;
    
    private boolean skipFailedBuilds;
    private boolean useAsBuildDisplayName; 
    
    public VersionNumberBuilder(String versionNumberString,
            String projectStartDate,
            String environmentVariableName,
            String environmentPrefixVariable,
            String buildsToday,
            String buildsThisWeek,
            String buildsThisMonth,
            String buildsThisYear,
            String buildsAllTime,
            boolean skipFailedBuilds) {
        this(versionNumberString, projectStartDate, environmentVariableName,
                environmentPrefixVariable, buildsToday, buildsThisWeek, buildsThisMonth,
                buildsThisYear, buildsAllTime, skipFailedBuilds, false);
    }
    
    @DataBoundConstructor
    public VersionNumberBuilder(String versionNumberString,
                                String projectStartDate,
                                String environmentVariableName,
                                String environmentPrefixVariable,
                                String buildsToday,
                                String buildsThisWeek,
                                String buildsThisMonth,
                                String buildsThisYear,
                                String buildsAllTime,
                                boolean skipFailedBuilds,
                                boolean useAsBuildDisplayName) {
        this.versionNumberString = versionNumberString;
        this.projectStartDate = VersionNumberCommon.parseDate(projectStartDate);
        this.environmentVariableName = environmentVariableName;
        this.environmentPrefixVariable = environmentPrefixVariable;
        this.skipFailedBuilds = skipFailedBuilds;
        this.useAsBuildDisplayName = useAsBuildDisplayName;
        
        this.oBuildsToday = makeValid(buildsToday);
        this.oBuildsThisWeek = makeValid(buildsThisWeek);
        this.oBuildsThisMonth = makeValid(buildsThisMonth);
        this.oBuildsThisYear = makeValid(buildsThisYear);
        this.oBuildsAllTime = makeValid(buildsAllTime);
    }
    
    public String getBuildsToday() {
        return this.oBuildsToday;
    }
    
    public String getBuildsThisWeek() {
        return this.oBuildsThisWeek;
    }
    
    public String getBuildsThisMonth() {
        return this.oBuildsThisMonth;
    }
    
    public String getBuildsThisYear() {
        return this.oBuildsThisYear;
    }
    
    public String getBuildsAllTime() {
        return this.oBuildsAllTime;
    }
    
    public boolean getSkipFailedBuilds() {
        return this.skipFailedBuilds;
    }
    
    public boolean getUseAsBuildDisplayName() {
        return this.useAsBuildDisplayName;
    }
    
    /**
     * Checks if the given string contains a valid value and returns that
     * value again if it is valid or returns an empty string if it is not. A
     * valid value encoded in the string must either be a (positive) number,
     * convertible to an integer or a reference to an environment-variable in
     * the form <code>${VARIABLE_NAME}</code> or <code>$VARIABLE_NAME</code>.
     * @param buildNum The (user-provided) string which should either contain
     *                 a number or a reference to an environment-variable.
     * @return The given <a>buildNum</a> if valid or an empty string.
     */
    private static String makeValid(String buildNum) {
        if (buildNum == null) return "";  // Return the default-value.
        try {
            buildNum = buildNum.trim();
            // If we got a valid integer the following conversion will
            // succeed without an exception.
            Integer intVal = Integer.valueOf(buildNum);
            if (intVal < 0)
                return "";  // Negative numbers are not allowed.
            else
                return intVal.toString();
        } catch (Exception e) {
            // Obviously, we did not receive a valid integer as override.
            // Is it a reference to an environment-variable?
            if (buildNum.matches(ENV_VAR_PATTERN)) {
                // Yes, so return it as-is and only retrieve its value when
                // the value must be accessed (to always get the most
                // up-to-date value).
                return buildNum;
            } else {
                // No, so it seems to be junk. Just return the default-value.
                return "";
            }
        }
    }
    
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getVersionNumberString() {
        return versionNumberString;
    }
    
    public String getProjectStartDate() {
        final DateFormat defaultDateFormat = new SimpleDateFormat(DEFAULT_DATE_FORMAT_PATTERN);
        return defaultDateFormat.format(projectStartDate);
    }
    public String getEnvironmentVariableName() {
        return this.environmentVariableName;
    }
    public String getEnvironmentPrefixVariable() {
        return this.environmentPrefixVariable;
    }
    private Run getPreviousBuildWithVersionNumber(Run build, BuildListener listener) {
        String envPrefix;
        
        if (this.environmentPrefixVariable != null) {
            try {
                EnvVars env = build.getEnvironment(listener);
                
                envPrefix = env.get(this.environmentPrefixVariable);
            } catch (IOException e) {
                envPrefix = null;
            } catch (InterruptedException e) {
                envPrefix = null;
            }
        } else {
            envPrefix = null;
        }

        return VersionNumberCommon.getPreviousBuildWithVersionNumber(build, envPrefix);
    }
    
    @SuppressWarnings("unchecked")
    private VersionNumberBuildInfo incBuild(Run build, BuildListener listener) throws IOException, InterruptedException {
        Map<String, String> enVars = build.getEnvironment(listener);
        Run prevBuild = getPreviousBuildWithVersionNumber(build, listener);
        VersionNumberBuildInfo incBuildInfo = VersionNumberCommon.incBuild(build, prevBuild, this.skipFailedBuilds);
        
        // have we overridden any of the version number info?  If so, set it up here
        boolean saveOverrides = false;
        Pattern pattern = Pattern.compile(ENV_VAR_PATTERN);

        int buildsToday = incBuildInfo.getBuildsToday();
        int buildsThisWeek = incBuildInfo.getBuildsThisWeek();
        int buildsThisMonth = incBuildInfo.getBuildsThisMonth();
        int buildsThisYear = incBuildInfo.getBuildsThisYear();
        int buildsAllTime = incBuildInfo.getBuildsAllTime();
        if (this.oBuildsToday == null || !this.oBuildsToday.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsToday = makeValid(oBuildsToday);
            int newVal = buildsToday;
            try {
                if (!oBuildsToday.matches(ENV_VAR_PATTERN)) {
                    newVal = Integer.parseInt(oBuildsToday);
                    oBuildsToday = "";  // Reset!
                } else {
                    Matcher m = pattern.matcher(oBuildsToday);
                    if (m.matches()) {
                      String varName = (m.group(1) != null) ? m.group(1) : m.group(2);
                      newVal = Integer.parseInt(enVars.get(varName));
                    }
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
            buildsToday = ((newVal >= 0) ? newVal : buildsToday);
        }
        if (this.oBuildsThisWeek == null || !this.oBuildsThisWeek.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsThisWeek = makeValid(oBuildsThisWeek);
            int newVal = buildsThisWeek;
            try {
                if (!oBuildsThisWeek.matches(ENV_VAR_PATTERN)) {
                    newVal = Integer.parseInt(oBuildsThisWeek);
                    oBuildsThisWeek = "";  // Reset!
                } else {
                    Matcher m = pattern.matcher(oBuildsThisWeek);
                    if (m.matches()) {
                        String varName = (m.group(1) != null) ? m.group(1) : m.group(2);
                        newVal = Integer.parseInt(enVars.get(varName));
                    }
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
            buildsThisWeek = ((newVal >= 0) ? newVal : buildsThisWeek);
        }
        if (this.oBuildsThisMonth == null || !this.oBuildsThisMonth.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsThisMonth = makeValid(oBuildsThisMonth);
            int newVal = buildsThisMonth;
            try {
                if (!oBuildsThisMonth.matches(ENV_VAR_PATTERN)) {
                    newVal = Integer.parseInt(oBuildsThisMonth);
                    oBuildsThisMonth = "";  // Reset!
                } else {
                    Matcher m = pattern.matcher(oBuildsThisMonth);
                    if (m.matches()) {
                      String varName = (m.group(1) != null) ? m.group(1) : m.group(2);
                      newVal = Integer.parseInt(enVars.get(varName));
                    }
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
            buildsThisMonth = ((newVal >= 0) ? newVal : buildsThisMonth);
        }
        if (this.oBuildsThisYear == null || !this.oBuildsThisYear.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsThisYear = makeValid(oBuildsThisYear);
            int newVal = buildsThisYear;
            try {
                if (!oBuildsThisYear.matches(ENV_VAR_PATTERN)) {
                    newVal = Integer.parseInt(oBuildsThisYear);
                    oBuildsThisYear = "";  // Reset!
                } else {
                    Matcher m = pattern.matcher(oBuildsThisYear);
                    if (m.matches()) {
                      String varName = (m.group(1) != null) ? m.group(1) : m.group(2);
                      newVal = Integer.parseInt(enVars.get(varName));
                    }
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
            buildsThisYear = ((newVal >= 0) ? newVal : buildsThisYear);
        }
        if (this.oBuildsAllTime == null || !this.oBuildsAllTime.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsAllTime = makeValid(oBuildsAllTime);
            int newVal = buildsAllTime;
            try {
                if (!oBuildsAllTime.matches(ENV_VAR_PATTERN)) {
                    newVal = Integer.parseInt(oBuildsAllTime);
                    oBuildsAllTime = "";  // Reset!
                } else {
                    Matcher m = pattern.matcher(oBuildsAllTime);
                    if (m.matches()) {
                      String varName = (m.group(1) != null) ? m.group(1) : m.group(2);
                      newVal = Integer.parseInt(enVars.get(varName));
                    }
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
            buildsAllTime = ((newVal >= 0) ? newVal : buildsAllTime);
        }
        
        // if we've used any of the overrides, reset them in the project
        if (saveOverrides) {
            build.getParent().save();
        }
        return new VersionNumberBuildInfo(buildsToday, buildsThisWeek, buildsThisMonth, buildsThisYear, buildsAllTime);
    }
    
    @SuppressWarnings("unchecked") @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
        String formattedVersionNumber = "";
        try {
            VersionNumberBuildInfo info = incBuild(build, listener);
            formattedVersionNumber = VersionNumberCommon.formatVersionNumber(this.versionNumberString,
                                                         this.projectStartDate,
                                                         info,
                                                         build.getEnvironment(listener),
                                                         build.getTimestamp());
            build.addAction(new VersionNumberAction(info, formattedVersionNumber));
            if (useAsBuildDisplayName) {
                build.setDisplayName(formattedVersionNumber);
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error(e.toString()));
            build.setResult(Result.FAILURE);
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error(e.toString()));
            build.setResult(Result.FAILURE);
        } catch (Exception e) {
            e.printStackTrace(listener.error(e.toString()));
            build.setResult(Result.FAILURE);
        }
        final String finalVersionNumber = formattedVersionNumber;
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put(environmentVariableName, finalVersionNumber);
            }
        };
    }
    
    @Override
    public BuildWrapperDescriptor getDescriptor() {
        return DESCRIPTOR;
    }
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    /**
     * Descriptor for {@link VersionNumberBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(VersionNumberBuilder.class);
            load();
        }
        
        
        
        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
        public FormValidation doCheckEnvironmentVariableName(@QueryParameter final String value) {
            if(value.length()==0)
                return FormValidation.error("Please set an environment variable name");
            else
                return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
        public FormValidation doCheckVersionNumberString(@QueryParameter final String value) {
            if(value.length()==0)
                return FormValidation.error("Please set a version number format string.  For more information, click on the ?.");
            else
            if(value.length()<4)
                return FormValidation.warning("Isn't the name too short?");
            else
                return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
        public FormValidation doCheckProjectStartDate(@QueryParameter final String value)  {
            if (value.length() > 0 && VersionNumberCommon.parseDate(value).compareTo(new Date(0)) == 0) {
                return FormValidation.error("Valid dates are in the format yyyy-mm-dd");
            } else {
                return FormValidation.ok();
            }
        }
        
        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Create a formatted version number";
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            return super.configure(req, json);
        }
        
        @Override
        public boolean isApplicable(AbstractProject<?, ?> proj) {
            return true;
        }
    }
}

