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
 * Common methods used by freestyle and pipeline jobs.
 */
public class VersionNumberCommon {
    
    private static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    
    // Pattern:   ${VAR_NAME} or $VAR_NAME
    public static final String ENV_VAR_PATTERN = "^(?:\\$\\{(\\w+)\\})|(?:\\$(\\w+))$";
    
    public static VersionNumberBuildInfo incBuild(Run build, EnvVars environmentVariables,
    		Run prevBuild, boolean skipFailedBuilds, String overrideBuildsAllTime) {
        int buildsToday = 1;
        int buildsThisWeek = 1;
        int buildsThisMonth = 1;
        int buildsThisYear = 1;
        int buildsAllTime = 1;
        // this is what we add to the previous version number to get builds today / this week / this month / this year / all time
        int buildInc = 1;

        /*
         * If we're overriding builds all time, figure it out here.  If we don't
         * it will be business as usual and we'll increment as needed.
         * 
         * It's necessary to do this here in case you want to override the build
         * number on the very first build.
         */
        if (isValidOverrideBuildsAllTime(environmentVariables, overrideBuildsAllTime)) {
        	buildsAllTime = getOverrideBuildsAllTime(environmentVariables, overrideBuildsAllTime);
        }
        
        if (prevBuild != null) {
            // if we're skipping version numbers on failed builds and the last build failed...
            if (skipFailedBuilds) {
                Result result = prevBuild.getResult();
                if (result != null && ! result.equals(Result.SUCCESS)) {
                    // don't increment
                    buildInc = 0;
                }
            }
            // get the current build date and the previous build date
            Calendar curCal = build.getTimestamp();
            Calendar todayCal = prevBuild.getTimestamp();
            
            // get the previous build version number information
            VersionNumberAction prevAction = (VersionNumberAction)prevBuild.getAction(VersionNumberAction.class);
            VersionNumberBuildInfo info = prevAction.getInfo();

            // increment builds per day
            if (curCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                    && curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
                    && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                buildsToday = info.getBuildsToday() + buildInc;
            } else {
                buildsToday = 1;
            }

            // increment builds per week
            if (curCal.get(Calendar.WEEK_OF_YEAR) == todayCal.get(Calendar.WEEK_OF_YEAR)
                    && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                buildsThisWeek = info.getBuildsThisWeek() + buildInc;
            } else {
                buildsThisWeek = 1;
            }

            // increment builds per month
            if (curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
                    && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                buildsThisMonth = info.getBuildsThisMonth() + buildInc;
            } else {
                buildsThisMonth = 1;
            }

            // increment builds per year
            if (curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                buildsThisYear = info.getBuildsThisYear() + buildInc;
            } else {
                buildsThisYear = 1;
            }

            /*
			 * If there's a valid override, we'll keep it, otherwise increment as normal.
			 * 
			 * TODO:  Not totally thrilled with this solution, but....
			 * 
			 * It's necessary to test this again here since we don't want to increment
			 * the number if we've already resolved this from the beginning of the method.
             */
            if (!isValidOverrideBuildsAllTime(environmentVariables, overrideBuildsAllTime)) {
	            // increment total builds
	            buildsAllTime = info.getBuildsAllTime() + buildInc;
            }
        }
        
        return new VersionNumberBuildInfo(buildsToday, buildsThisWeek, buildsThisMonth, buildsThisYear, buildsAllTime);
    }
    
    public static Run getPreviousBuildWithVersionNumber(Run build, String envPrefix) {        
        // a build that fails early will not have a VersionNumberAction attached
        Run prevBuild = build.getPreviousBuild();
        
        while (prevBuild != null) {
            VersionNumberAction prevAction = (VersionNumberAction)prevBuild.getAction(VersionNumberAction.class);
            
            if (prevAction != null) {
                if (envPrefix != null) {
                    String version = prevAction.getVersionNumber();
    
                    if (version.startsWith(envPrefix)) {
                        return prevBuild;
                    }
                } else {
                    return prevBuild;
                }
            }
            
            prevBuild = prevBuild.getPreviousBuild();
        }
        
        return null;
    }
    
    public static Date parseDate(String dateString) {
        try {
            final DateFormat defaultDateFormat = new SimpleDateFormat(DEFAULT_DATE_FORMAT_PATTERN);
            return defaultDateFormat.parse(dateString);
        } catch (Exception e) {
            return new Date(0);
        }
    }
    
    /**
     * Returns true if the passed builds all time value resolves to a value greater
     * than or equal to 0 based on either an environment variable or direct integer
     * parsing.
     * 
     * @param envVars
     * @param buildsAllTime
     * @return
     */
    private static boolean isValidOverrideBuildsAllTime(EnvVars envVars, String buildsAllTime) {
    	boolean result = false;
    	
    	if (buildsAllTime != null && !buildsAllTime.equals("")) {
    		result = getOverrideBuildsAllTime(envVars, buildsAllTime) != null;
    	}
    	
    	return result;
    }
    
    /**
     * Given an override builds all time, first check if it is an environment variable that
     * resolves, otherwise try converting directly to an int.
     * 
     * @param envVars The environment variables to the build.
     * @param buildsAllTime A string either resolving to an int or an environment variable that can provide the next value.
     * @return The new build number or null if we can't resolve as a number greater than or equal to 0
     */
    private static Integer getOverrideBuildsAllTime(EnvVars envVars, String buildsAllTime) {
    	Integer result = null;
        Pattern pattern = Pattern.compile(VersionNumberCommon.ENV_VAR_PATTERN);

		// Just in case someone directly edited the config-file with invalid values.
		buildsAllTime = makeValid(buildsAllTime);

		try {
		    if (!buildsAllTime.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
		        result = Integer.parseInt(buildsAllTime);
		        buildsAllTime = "";  // Reset!
		    } else {
		        Matcher m = pattern.matcher(buildsAllTime);
		        if (m.matches()) {
		          String varName = (m.group(1) != null) ? m.group(1) : m.group(2);
		          result = Integer.parseInt(envVars.get(varName));
		        }
		    }
		} catch (Exception e) {
		    // Invalid value, so do not override!
		}
		
		if (result == null || result < 0) {
			result = null;
		}

		return result;
    }
    
	public static String formatVersionNumber(String versionNumberFormatString,
                                             Date projectStartDate,
                                             VersionNumberBuildInfo info,
                                             Map<String, String> enVars,
                                             Calendar buildDate) {
        // Expand all environment-variables in the format-string.
        String vnf = new EnvVars(enVars).expand(versionNumberFormatString);
        
        // Try to expand all remaining (version-number specific) variables.
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
                } else if ("BUILD_WEEK".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.WEEK_OF_YEAR)), argumentString.length());
                } else if ("BUILD_MONTH".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.MONTH) + 1), argumentString.length());
                } else if ("BUILD_YEAR".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.YEAR)), argumentString.length());
                } else if ("BUILDS_TODAY".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsToday()), argumentString.length());
                } else if ("BUILDS_THIS_WEEK".equals(expressionKey)) {
                    replaceValue = sizeTo(Integer.toString(info.getBuildsThisWeek()), argumentString.length());
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
                } else if (("MONTHS_SINCE_PROJECT_START".equals(expressionKey)) && (projectStartDate != null)) {
                    Calendar projectStartCal = Calendar.getInstance();
                    projectStartCal.setTime(projectStartDate);
                    int monthsSinceStart = buildDate.get(Calendar.MONTH) - projectStartCal.get(Calendar.MONTH);
                    monthsSinceStart += (buildDate.get(Calendar.YEAR) - projectStartCal.get(Calendar.YEAR)) * 12;
                    replaceValue = sizeTo(Integer.toString(monthsSinceStart), argumentString.length());
                } else if (("YEARS_SINCE_PROJECT_START".equals(expressionKey)) && (projectStartDate != null)) {
                    Calendar projectStartCal = Calendar.getInstance();
                    projectStartCal.setTime(projectStartDate);
                    int yearsSinceStart = buildDate.get(Calendar.YEAR) - projectStartCal.get(Calendar.YEAR);
                    replaceValue = sizeTo(Integer.toString(yearsSinceStart), argumentString.length());
                }
                // if it's not one of the defined values, check the environment variables
                else {
                    if (enVars != null) {
                        for (Map.Entry entry : enVars.entrySet()) {
                            if (entry.getKey().equals(expressionKey)) {
                                replaceValue = (String)entry.getValue();
                            }
                        }
                    }
                }
                vnf = vnf.substring(0, blockStart) + replaceValue + vnf.substring(blockEnd, vnf.length());
            }
        } while (blockStart >= 0);
        
        return vnf;
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
    public static String makeValid(String buildNum) {
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
            if (buildNum.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
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
    
    private static String sizeTo(String s, int length) {
        while (s.length() < length) {
            s = "0" + s;
        }
        return s;
    }
}

