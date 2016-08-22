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
    
    public static VersionNumberBuildInfo incBuild(Run build, Run prevBuild, boolean skipFailedBuilds) {
        int buildsToday = 1;
        int buildsThisWeek = 1;
        int buildsThisMonth = 1;
        int buildsThisYear = 1;
        int buildsAllTime = 1;
        // this is what we add to the previous version number to get builds today / this week / this month / this year / all time
        int buildInc = 1;
        
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

            // increment total builds
            buildsAllTime = info.getBuildsAllTime() + buildInc;
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
    
    private static String sizeTo(String s, int length) {
        while (s.length() < length) {
            s = "0" + s;
        }
        return s;
    }
}

