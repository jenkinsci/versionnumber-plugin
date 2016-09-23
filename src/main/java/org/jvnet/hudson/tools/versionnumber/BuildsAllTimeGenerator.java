package org.jvnet.hudson.tools.versionnumber;

import hudson.model.Run;

public class BuildsAllTimeGenerator extends AbstractBuildNumberGenerator {

	@Override
	public int resolveValue(Run build, Run prevBuild, int increment) {
		int nextNumber;
		
        // get the previous build version number information
        VersionNumberBuildInfo info = getPreviousBuildInfo(prevBuild);

        nextNumber = info.getBuildsAllTime() + increment;
        
        return nextNumber;
		
	}

}
