package org.jvnet.hudson.tools.versionnumber;

import hudson.Extension;
import hudson.Launcher;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

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
 * and this contains the builds today/this month/ this year/ all time.  When incrementing
 * each of these values, unless they're overridden in the configuration the value from
 * the previous build will be used.
 * </p>
 *
 * @author Carl Lischeske - NETFLIX
 */
public class VersionNumberBuilder extends BuildWrapper {
    
    private static final DateFormat defaultDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    private final String versionNumberString;
    private final Date projectStartDate;
    private final String environmentVariableName;
    
    private int oBuildsToday;
    private int oBuildsThisMonth;
    private int oBuildsThisYear;
    private int oBuildsAllTime;
    
    private boolean skipFailedBuilds;
    
    @DataBoundConstructor
    public VersionNumberBuilder(String versionNumberString,
                                String projectStartDate,
                                String environmentVariableName,
                                String buildsToday,
                                String buildsThisMonth,
                                String buildsThisYear,
                                String buildsAllTime,
                                boolean skipFailedBuilds) {
        this.versionNumberString = versionNumberString;
        this.projectStartDate = parseDate(projectStartDate);
        this.environmentVariableName = environmentVariableName;
        this.skipFailedBuilds = skipFailedBuilds;
        
        try {
            oBuildsToday = Integer.parseInt(buildsToday);
        } catch (Exception e) {
            oBuildsToday = -1;
        }
        try {
            oBuildsThisMonth = Integer.parseInt(buildsThisMonth);
        } catch (Exception e) {
            oBuildsThisMonth = -1;
        }
        try {
            oBuildsThisYear = Integer.parseInt(buildsThisYear);
        } catch (Exception e) {
            oBuildsThisYear = -1;
        }
        try {
            oBuildsAllTime = Integer.parseInt(buildsAllTime);
        } catch (Exception e) {
            oBuildsAllTime = -1;
        }
        
    }
    
    public String getBuildsToday() {
    	return "";
    }
    
    public String getBuildsThisMonth() {
    	return "";
    }
    
    public String getBuildsThisYear() {
    	return "";
    }
    
    public String getBuildsAllTime() {
    	return "";
    }
    
    public boolean getSkipFailedBuilds() {
    	return this.skipFailedBuilds;
    }
    
    private static Date parseDate(String dateString) {
    	try {
            return defaultDateFormat.parse(dateString);
    	} catch (Exception e) {
            return new Date(0);
    	}
    }
    
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getVersionNumberString() {
        return versionNumberString;
    }
    
    public String getProjectStartDate() {
    	return defaultDateFormat.format(projectStartDate);
    }
    public String getEnvironmentVariableName() {
    	return this.environmentVariableName;
    }
    
    @SuppressWarnings("unchecked")
    private VersionNumberBuildInfo incBuild(AbstractBuild build, PrintStream log) throws IOException {
    	Run prevBuild = build.getPreviousBuild();
    	int buildsToday = 1;
    	int buildsThisMonth = 1;
    	int buildsThisYear = 1;
    	int buildsAllTime = 1;
    	// this is what we add to the previous version number to get builds today/this month/ this year/all time
    	int buildInc = 1;
    	
    	// if we're skipping version numbers on failed builds and the last build failed...
    	if (skipFailedBuilds && !prevBuild.getResult().equals(Result.SUCCESS)) {
            // don't increment
            buildInc = 0;
    	}
    	if (prevBuild != null) {
            // get the current build date and the previous build date
            Calendar curCal = build.getTimestamp();
            Calendar todayCal = prevBuild.getTimestamp();
            
            // get the previous build version number information
            VersionNumberAction prevAction = (VersionNumberAction)prevBuild.getAction(VersionNumberAction.class);
            if (prevAction != null) {
                VersionNumberBuildInfo info = prevAction.getInfo();
    		
                // increment builds per day
    	    	if (
                    curCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                    && curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
                    && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                    ) {
                    buildsToday = info.getBuildsToday() + buildInc;
    	    	} else {
                    buildsToday = 1;
    	    	}
    	    	
    	    	// increment builds per month
    	    	if (
                    curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
                    && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                    ) {
                    buildsThisMonth = info.getBuildsThisMonth() + buildInc;
    	    	} else {
                    buildsThisMonth = 1;
    	    	}
    	    	
    	    	// increment builds per year
    	    	if (
                    curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                    ) {
                    buildsThisYear = info.getBuildsThisYear() + buildInc;
    	    	} else {
                    buildsThisYear = 1;
    	    	}
    	    	
    	    	// increment total builds
    	    	buildsAllTime = info.getBuildsAllTime() + buildInc;
            }
    	}
    	// have we overridden any of the version number info?  If so, set it up here
    	boolean saveOverrides = false;
    	if (this.oBuildsToday >= 0) {
            buildsToday = oBuildsToday;
            oBuildsToday = -1;
            saveOverrides = true;
    	}
    	if (this.oBuildsThisMonth >= 0) {
            buildsThisMonth = oBuildsThisMonth;
            oBuildsThisMonth = -1;
            saveOverrides = true;
    	}
    	if (this.oBuildsThisYear >= 0) {
            buildsThisYear = oBuildsThisYear;
            oBuildsThisYear = -1;
            saveOverrides = true;
    	}
    	if (this.oBuildsAllTime >= 0) {
            buildsAllTime = oBuildsAllTime;
            oBuildsAllTime = -1;
            saveOverrides = true;
    	}
    	// if we've used any of the overrides, reset them in the project
    	if (saveOverrides) {
            build.getProject().save();
    	}
    	return new VersionNumberBuildInfo(buildsToday, buildsThisMonth, buildsThisYear, buildsAllTime);
    }
    
    private static String formatVersionNumber(String versionNumberFormatString,
                                              Date projectStartDate,
                                              VersionNumberBuildInfo info,
                                              Map<String, String> enVars,
                                              Calendar buildDate,
                                              PrintStream log) {
    	String vnf = new String(versionNumberFormatString);
    	
    	int blockStart = 0;
    	do {
            // blockStart and blockEnd define the starting and ending positions of the entire block, including
            // the ${}
            blockStart = vnf.indexOf("${");
            if (blockStart >= 0) {
                int blockEnd = vnf.indexOf("}", blockStart) + 1;
                // if this is an unclosed block...
                if (blockEnd <= blockStart) {
                    // include everything up to the unclosed block, then exit
                    vnf = vnf.substring(0, blockStart);
                    break;
                }
                // command start/end include only the actual name of the variable to be replaced
                int commandStart = blockStart + 2;
                int commandEnd = blockEnd - 1;
                int argumentStart = vnf.indexOf(",", blockStart);
                int argumentEnd = 0;
                if (argumentStart > 0 && argumentStart < blockEnd) {
                    argumentEnd = blockEnd - 1;
                    commandEnd = argumentStart;
                }
                String expressionKey = vnf.substring(commandStart, commandEnd);
                String argumentString = argumentEnd > 0 ? vnf.substring(argumentStart + 1, argumentEnd).trim() : "";
                String replaceValue = "";
    		
                // we have the expression key; if it's any known key, fill in the value
                if ("".equals(expressionKey)) {
                    replaceValue = "";
                } else if ("BUILD_DATE_FORMATTED".equals(expressionKey)) {
                    DateFormat fmt = SimpleDateFormat.getInstance();
                    if (!"".equals(argumentString)) {
                        // this next line is a bit tricky, but basically, we're looking returning everything
                        // inside a pair of quote marks; in other words, everything from after the first quote
                        // to before the second
                        String fmtString = argumentString.substring(argumentString.indexOf('"') + 1, argumentString.indexOf('"', argumentString.indexOf('"') + 1));
                        fmt = new SimpleDateFormat(fmtString);
                    }
                    replaceValue = fmt.format(buildDate.getTime());
                } else if ("BUILD_DAY".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.DAY_OF_MONTH)), argumentString.length());
                } else if ("BUILD_MONTH".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.MONTH) + 1), argumentString.length());
                } else if ("BUILD_YEAR".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.YEAR)), argumentString.length());
                } else if ("BUILDS_TODAY".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsToday()), argumentString.length());
                } else if ("BUILDS_THIS_MONTH".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsThisMonth()), argumentString.length());
                } else if ("BUILDS_THIS_YEAR".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsThisYear()), argumentString.length());
                } else if ("BUILDS_ALL_TIME".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsAllTime()), argumentString.length());
                } else if ("BUILDS_TODAY_Z".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsToday() - 1), argumentString.length());
                } else if ("BUILDS_THIS_MONTH_Z".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsThisMonth() - 1), argumentString.length());
                } else if ("BUILDS_THIS_YEAR_Z".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsThisYear() - 1), argumentString.length());
                } else if ("BUILDS_ALL_TIME_Z".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsAllTime() - 1), argumentString.length());
                } else if ("MONTHS_SINCE_PROJECT_START".equals(expressionKey)) {
                    Calendar projectStartCal = Calendar.getInstance();
                    projectStartCal.setTime(projectStartDate);
                    int monthsSinceStart = buildDate.get(Calendar.MONTH) - projectStartCal.get(Calendar.MONTH);
                    monthsSinceStart += (buildDate.get(Calendar.YEAR) - projectStartCal.get(Calendar.YEAR)) * 12;
                    replaceValue = sizeTo(Integer.toString(monthsSinceStart), argumentString.length());
                } else if ("YEARS_SINCE_PROJECT_START".equals(expressionKey)) {
                    Calendar projectStartCal = Calendar.getInstance();
                    projectStartCal.setTime(projectStartDate);
                    int yearsSinceStart = buildDate.get(Calendar.YEAR) - projectStartCal.get(Calendar.YEAR);
                    replaceValue = sizeTo(Integer.toString(yearsSinceStart), argumentString.length());
                }
                // if it's not one of the defined values, check the environment variables
                else {
                    for (String enVarKey : enVars.keySet()) {
                        if (enVarKey.equals(expressionKey)) {
                            replaceValue = enVars.get(enVarKey);
                        }
                    }
                }
                vnf = vnf.substring(0, blockStart) + replaceValue + vnf.substring(blockEnd, vnf.length());
            }
    	} while (blockStart >= 0);
    	
    	return vnf;
    }
    
    private static String sizeTo(String s, int length) {
        while (s.length() < length) {
            s = "0" + s;
        }
        return s;
    }
    
    @SuppressWarnings("unchecked") @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	String formattedVersionNumber = "";
    	try {
            VersionNumberBuildInfo info = incBuild(build, listener.getLogger());
            formattedVersionNumber = formatVersionNumber(this.versionNumberString,
                                                         this.projectStartDate,
                                                         info,
                                                         build.getEnvironment(listener),
                                                         build.getTimestamp(),
                                                         listener.getLogger()
                                                         );
            build.addAction(new VersionNumberAction(info, formattedVersionNumber));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            listener.error(e.toString());
            build.setResult(Result.FAILURE);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            listener.error(e.toString());
            build.setResult(Result.FAILURE);
        } catch (Exception e) {
            listener.error(e.toString());
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
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl)super.getDescriptor();
    }
    
    /**
     * Descriptor for {@link VersionNumberBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
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
            if (value.length() > 0 && parseDate(value).compareTo(new Date(0)) == 0) {
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

