package org.jvnet.hudson.tools.versionnumber;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;

public abstract class AbstractBuildNumberGenerator implements BuildNumberGenerator {
        
    @Override
    public int getNextNumber(Run build, EnvVars vars, Run prevBuild, Result worstResultForIncrement, String override) {
        int nextNumber = 1;
        
        // Attempt an override
        if (override != null && isValidOverride(vars, override)) {
            nextNumber = resolveOverride(vars, override);
        // If no override, start from the previous build
        } else if (prevBuild != null) {
            int increment = 1;
            // we're skipping version numbers if the last build's result was worse than required...
            Result result = prevBuild.getResult();
            if (result != null && result.isWorseThan(worstResultForIncrement)) {
                // don't increment
                increment = 0;
            }
            
            nextNumber = resolveValue(build, prevBuild, increment);
        }
        
        return nextNumber;
    }
    
    protected VersionNumberBuildInfo getPreviousBuildInfo(Run prevBuild) {
        VersionNumberAction prevAction = (VersionNumberAction)prevBuild.getAction(VersionNumberAction.class);
        VersionNumberBuildInfo info = prevAction.getInfo();
        return info;
    }
            
    /**
     * Returns true if the passed override results to a valid value greater than
     * or equal to 0, false otherwise.
     * 
     * @param envVars The environment variables.
     * @param override The override string, such as buildsAllTime
     * @return True if the override results in a valid value.
     */
    public static boolean isValidOverride(EnvVars envVars, String override) {
        return resolveOverride(envVars, override) != null;
    }
    
    /**
     * Given an override, see if it resolves to a valid integer greater than
     * or equal to zero from either an environment variable or a direct
     * conversion.
     * 
     * @param envVars The environment variables.
     * @param override The override string, such as buildsAllTime
     * @return The integer value of the override or null if conversion
     * does not result in a valid value.
     */
    public static Integer resolveOverride(EnvVars envVars, String override) {
        Integer result = null;
        Pattern pattern = Pattern.compile(VersionNumberCommon.ENV_VAR_PATTERN);

        // Just in case someone directly edited the config-file with invalid values.
        override = VersionNumberCommon.makeValid(override);

        try {
            if (!override.matches(VersionNumberCommon.ENV_VAR_PATTERN)) {
                result = Integer.parseInt(override);
                override = "";  // Reset!
            } else {
                Matcher m = pattern.matcher(override);
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
    
}
