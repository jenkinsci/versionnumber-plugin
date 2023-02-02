package org.jvnet.hudson.tools.versionnumber;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;

public interface BuildNumberGenerator {

    int getNextNumber(Run build, EnvVars vars, Run prevBuild, Result worstResultForIncrement, String override);
    
    int resolveValue(Run build, Run previousBuild, int increment);
    
}
