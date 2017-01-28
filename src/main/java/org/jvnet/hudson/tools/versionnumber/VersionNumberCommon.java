package org.jvnet.hudson.tools.versionnumber;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;
import java.lang.invoke.MethodHandles;

import hudson.EnvVars;
import hudson.model.Run;

/**
 * Common methods used by freestyle and pipeline jobs.
 */
public class VersionNumberCommon {
    
    /** Use Java 7 MethodHandles to get my class for logger. */
    private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
    
    private static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";

    // Pattern:   ${VAR_NAME} or $VAR_NAME
    public static final String ENV_VAR_PATTERN = "^(?:\\$\\{(\\w+)\\})|(?:\\$(\\w+))$";
    
    public static VersionNumberBuildInfo incBuild(Run build, EnvVars vars,
            Run prevBuild, boolean skipFailedBuilds, String overrideBuildsToday, String overrideBuildsThisWeek,
            String overrideBuildsThisMonth, String overrideBuildsThisYear, String overrideBuildsAllTime) {
        
        int buildsToday = new BuildsTodayGenerator().getNextNumber(build, vars, prevBuild, skipFailedBuilds, overrideBuildsToday);
        int buildsThisWeek = new BuildsThisWeekGenerator().getNextNumber(build, vars, prevBuild, skipFailedBuilds, overrideBuildsThisWeek);
        int buildsThisMonth = new BuildsThisMonthGenerator().getNextNumber(build, vars, prevBuild, skipFailedBuilds, overrideBuildsThisMonth);
        int buildsThisYear = new BuildsThisYearGenerator().getNextNumber(build, vars, prevBuild, skipFailedBuilds, overrideBuildsThisYear);
        int buildsAllTime = new BuildsAllTimeGenerator().getNextNumber(build, vars, prevBuild, skipFailedBuilds, overrideBuildsAllTime);
                
        return new VersionNumberBuildInfo(buildsToday, buildsThisWeek, buildsThisMonth, buildsThisYear, buildsAllTime);
    }
    
    public static Run getPreviousBuildWithVersionNumber(Run build, String envPrefix) {        
        // a build that fails early will not have a VersionNumberAction attached
        Run prevBuild = build.getPreviousBuild();
        
        while (prevBuild != null) {
            VersionNumberAction prevAction = (VersionNumberAction)prevBuild.getAction(VersionNumberAction.class);

            if (prevAction == null) 
                LOGGER.fine("prevAction.getVersionNumber() : 'null'");
            else
                LOGGER.fine("prevAction.getVersionNumber() : '" + prevAction.getVersionNumber() + "'");
            
            if (prevAction != null) {
                if (envPrefix != null) {
                    String version = prevAction.getVersionNumber();
    
                    if (version.startsWith(envPrefix)) {
                        LOGGER.info("Previous build's version-number: '" + prevAction.getVersionNumber() + "'");
                        return prevBuild;
                    }
                } else {
                    LOGGER.info("Previous build's version-number: '" + prevAction.getVersionNumber() + "'");
                    return prevBuild;
                }
            }
            
            prevBuild = prevBuild.getPreviousBuild();
        }
        
        LOGGER.info("Previous build's version-number: N/A");
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
        
    public static String formatVersionNumber(String versionNumberFormatString,
                                             Date projectStartDate,
                                             VersionNumberBuildInfo info,
                                             Map<String, String> enVars,
                                             Calendar buildDate) {
        LOGGER.info("Version-number format-string before expansion of env-variables: '" + versionNumberFormatString + "'");
        // Expand all environment-variables in the format-string.
        String vnf = new EnvVars(enVars).expand(versionNumberFormatString);
        LOGGER.info("Version-number format-string after expansion of env-variables: '" + vnf + "'");
        
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
                // if it's not one of the defined values, check the environment variables (again)
                // NOTE: This probably means, that an environment-variable resolves to itself, which
                //       might result in an infinite loop. Check for this!
                else {
                    LOGGER.fine("Special case: A variable could not be resolved. (Does it resolve to itself?)" +
                                " [var == " + expressionKey + "]");
                    if (enVars != null) {
                        for (Map.Entry entry : enVars.entrySet()) {
                            if (entry.getKey().equals(expressionKey)) {
                                // Check for variable which resolves to itself!
                                if (entry.getValue().equals("${" + expressionKey + "}")) {
                                    LOGGER.fine("Yes, the variable resolves to itself. Ignoring it." +
                                                " [var == " + expressionKey + "]");
                                } else {
                                    LOGGER.fine("No, the variable does not resolve to itself. Using it." +
                                                " [var == " + expressionKey + "]");
                                    replaceValue = (String)entry.getValue();
                                    // Probably just use a substring of the value?
                                    replaceValue = selectSubstringOfReplaceValue(replaceValue, argumentString);
                                }
                            }
                        }
                    }
                }
                vnf = vnf.substring(0, blockStart) + replaceValue + vnf.substring(blockEnd, vnf.length());
            }
        } while (blockStart >= 0);
        
        LOGGER.info("Version-number format-string after expansion of all variables: '" + vnf + "'");
        return vnf;
    }
    
    private static String selectSubstringOfReplaceValue(String replaceValue, String argumentString) {
        LOGGER.info("Before selecting a substring of the replace-value. [replaceValue == " + replaceValue + ", argumentString == " + argumentString + "]");
        
        // We will use the below lines to limit the number of character we want to 
        // use from the front or the back of the replace-value (aka environment variable).

        // Make sure there is an argument string and that it is surrounded by double-quotes!
        if (!"".equals(argumentString) && argumentString.length() >= 3 &&
                argumentString.charAt(0) == '"' && argumentString.charAt(argumentString.length() - 1) == '"') {
            // Strip quotes from argument-string.
            String fmtString = argumentString.substring(1, argumentString.length() - 1);
            // Make sure it only contains a positive or negative whole number.
            if (fmtString.matches("^(\\+|-)?\\d+$")) {
                Integer fmtInt = Integer.parseInt(fmtString);
                // if it's not smaller than the length of the value, we will use the whole value
                if (Math.abs(fmtInt.intValue()) < replaceValue.length()) {
                    if (fmtInt > 0) {
                        replaceValue = replaceValue.substring(0, fmtInt);
                    } else if (fmtInt < 0) {
                        replaceValue = replaceValue.substring(replaceValue.length() + fmtInt);
                    }
                }
            }
        }
        LOGGER.info("After selecting a substring of the replace-value. [replaceValue == " + replaceValue + "]");
        return replaceValue;
    }
    
    /**
     * Checks if the given string contains a valid value and returns that
     * value again if it is valid or returns an empty string if it is not. A
     * valid value encoded in the string must either be a (positive) number,
     * convertible to an integer or a reference to an environment-variable in
     * the form <code>${VARIABLE_NAME}</code> or <code>$VARIABLE_NAME</code>.
     * @param value The (user-provided) string which should either contain
     *                 a number or a reference to an environment-variable.
     * @return The given <a>buildNum</a> if valid or an empty string.
     */
    public static String makeValid(String value) {
        if (value == null) return "";  // Return the default-value.
        try {
            value = value.trim();
            // If we got a valid integer the following conversion will
            // succeed without an exception.
            Integer intVal = Integer.valueOf(value);
            if (intVal < 0)
                return "";  // Negative numbers are not allowed.
            else
                return intVal.toString();
        } catch (Exception e) {
            // Obviously, we did not receive a valid integer as override.
            // Is it a reference to an environment-variable?
            if (value.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                // Yes, so return it as-is and only retrieve its value when
                // the value must be accessed (to always get the most
                // up-to-date value).
                return value;
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

