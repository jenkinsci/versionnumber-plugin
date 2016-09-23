package org.jvnet.hudson.tools.versionnumber;

import java.util.Calendar;

import hudson.model.Run;

public class BuildsThisWeekGenerator extends AbstractBuildNumberGenerator {

	@Override
	public int resolveValue(Run build, Run prevBuild, int increment) {
		int nextNumber;
		
        // get the current build date and the previous build date
        Calendar curCal = build.getTimestamp();
        Calendar todayCal = prevBuild.getTimestamp();
        
        // get the previous build version number information
        VersionNumberBuildInfo info = getPreviousBuildInfo(prevBuild);

        // increment builds per week
        if (curCal.get(Calendar.WEEK_OF_YEAR) == todayCal.get(Calendar.WEEK_OF_YEAR)
                && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
        	nextNumber = info.getBuildsThisWeek() + increment;
        } else {
        	nextNumber = 1;
        }
        
        return nextNumber;
		
	}

}
