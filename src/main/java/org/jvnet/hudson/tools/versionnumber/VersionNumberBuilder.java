package org.jvnet.hudson.tools.versionnumber;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.lang.invoke.MethodHandles;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

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
    
    /** Use Java 7 MethodHandles to get my class for logger. */
    private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
    
    private final String versionNumberString;
    private final Date projectStartDate;
    private final String environmentVariableName;
    private final String environmentPrefixVariable;
    
    private String oBuildsToday;
    private String oBuildsThisWeek;
    private String oBuildsThisMonth;
    private String oBuildsThisYear;
    private String oBuildsAllTime;
    
    private String  worstResultForIncrement = null;
    @Deprecated
    private boolean skipFailedBuilds = false;
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
                buildsThisYear, buildsAllTime,
                (skipFailedBuilds) ? VersionNumberCommon.WORST_RESULT_SUCCESS : VersionNumberCommon.WORST_RESULT_NOT_BUILT,
                false);
    }
    
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
        this(versionNumberString, projectStartDate, environmentVariableName,
                environmentPrefixVariable, buildsToday, buildsThisWeek, buildsThisMonth,
                buildsThisYear, buildsAllTime,
                (skipFailedBuilds) ? VersionNumberCommon.WORST_RESULT_SUCCESS : VersionNumberCommon.WORST_RESULT_NOT_BUILT,
                useAsBuildDisplayName);
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
                                String worstResultForIncrement,
                                boolean useAsBuildDisplayName) {
        this.versionNumberString = versionNumberString;
        this.projectStartDate = VersionNumberCommon.parseDate(projectStartDate);
        this.environmentVariableName = environmentVariableName;
        this.environmentPrefixVariable = environmentPrefixVariable;
        this.worstResultForIncrement = worstResultForIncrement;
        this.useAsBuildDisplayName = useAsBuildDisplayName;
        
        this.oBuildsToday = VersionNumberCommon.makeValid(buildsToday);
        this.oBuildsThisWeek = VersionNumberCommon.makeValid(buildsThisWeek);
        this.oBuildsThisMonth = VersionNumberCommon.makeValid(buildsThisMonth);
        this.oBuildsThisYear = VersionNumberCommon.makeValid(buildsThisYear);
        this.oBuildsAllTime = VersionNumberCommon.makeValid(buildsAllTime);
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
    
    public Result getWorstResultForIncrement() {
        // For compatibility-reasons during transition from old plugin (<= 1.8.1) to newer plugin.
        if (this.skipFailedBuilds) {
            LOGGER.warning("At least in one project VersionNumber plugin still uses the old config-variable 'skipFailedBuilds'. Make sure to update and safe the job-configs to update that behavior.");
            this.skipFailedBuilds = false;
            this.worstResultForIncrement = VersionNumberCommon.WORST_RESULT_SUCCESS;
        }
        if (this.worstResultForIncrement == null) {
            this.worstResultForIncrement = VersionNumberCommon.WORST_RESULT_NOT_BUILT;
        }
        switch (this.worstResultForIncrement) {
            case VersionNumberCommon.WORST_RESULT_NOT_BUILT:
                return Result.NOT_BUILT;
            case VersionNumberCommon.WORST_RESULT_ABORTED:
                return Result.ABORTED;
            case VersionNumberCommon.WORST_RESULT_FAILURE:
                return Result.FAILURE;
            case VersionNumberCommon.WORST_RESULT_UNSTABLE:
                return Result.UNSTABLE;
            case VersionNumberCommon.WORST_RESULT_SUCCESS:
                return Result.SUCCESS;
            default:
                this.worstResultForIncrement = VersionNumberCommon.WORST_RESULT_SUCCESS;
                return Result.SUCCESS;
        }
    }
    
    public boolean getUseAsBuildDisplayName() {
        return this.useAsBuildDisplayName;
    }
        
    /**
     * We'll use this from the <code>config.jelly</code>.
     */
    public String getVersionNumberString() {
        return versionNumberString;
    }
    
    public String getProjectStartDate() {
        final DateFormat defaultDateFormat = new SimpleDateFormat(VersionNumberCommon.DEFAULT_DATE_FORMAT_PATTERN);
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
        EnvVars enVars = build.getEnvironment(listener);
        Run prevBuild = getPreviousBuildWithVersionNumber(build, listener);
        VersionNumberBuildInfo incBuildInfo = VersionNumberCommon.incBuild(build, enVars, prevBuild,
                this.getWorstResultForIncrement(),
                this.oBuildsToday,
                this.oBuildsThisWeek,
                this.oBuildsThisMonth,
                this.oBuildsThisYear,
                this.oBuildsAllTime);
        
        // have we overridden any of the version number info?  If so, set it up here
        boolean saveOverrides = false;
        
        if (this.oBuildsToday == null || !this.oBuildsToday.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsToday = VersionNumberCommon.makeValid(oBuildsToday);
            try {
                if (!oBuildsToday.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                    oBuildsToday = "";  // Reset!
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
        }
        
        if (this.oBuildsThisWeek == null || !this.oBuildsThisWeek.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsThisWeek = VersionNumberCommon.makeValid(oBuildsThisWeek);
            try {
                if (!oBuildsThisWeek.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                    oBuildsThisWeek = "";  // Reset!
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
        }
        if (this.oBuildsThisMonth == null || !this.oBuildsThisMonth.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsThisMonth = VersionNumberCommon.makeValid(oBuildsThisMonth);
            try {
                if (!oBuildsThisMonth.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                    oBuildsThisMonth = "";  // Reset!
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
        }
        if (this.oBuildsThisYear == null || !this.oBuildsThisYear.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            // Just in case someone directly edited the config-file with invalid values.
            oBuildsThisYear = VersionNumberCommon.makeValid(oBuildsThisYear);
            try {
                if (!oBuildsThisYear.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                    oBuildsThisYear = "";  // Reset!
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
        }
        
        if (this.oBuildsAllTime == null || !this.oBuildsAllTime.equals("")) {
            saveOverrides = true;  // Always need to save if not empty!
            oBuildsAllTime = VersionNumberCommon.makeValid(oBuildsAllTime);
            try {
                if (!oBuildsAllTime.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                    oBuildsAllTime = "";  // Reset!
                }
            } catch (Exception e) {
                // Invalid value, so do not override!
            }
        }
        
        // if we've used any of the overrides, reset them in the project
        if (saveOverrides) {
            build.getParent().save();
        }
        return incBuildInfo;
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
         * Performs on-the-fly validation of the form field 'environmentVariableName'.
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
         * Performs on-the-fly validation of the form field 'versionNumberString'.
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
         * Performs on-the-fly validation of the form field 'projectStartDate'.
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

        public ListBoxModel doFillWorstResultForIncrementItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(VersionNumberCommon.WORST_RESULT_SUCCESS);
            items.add(VersionNumberCommon.WORST_RESULT_UNSTABLE);
            items.add(VersionNumberCommon.WORST_RESULT_FAILURE);
            items.add(VersionNumberCommon.WORST_RESULT_ABORTED);
            items.add(VersionNumberCommon.WORST_RESULT_NOT_BUILT);
            return items;
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

