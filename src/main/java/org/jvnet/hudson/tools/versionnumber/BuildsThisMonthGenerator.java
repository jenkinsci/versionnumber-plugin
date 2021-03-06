package org.jvnet.hudson.tools.versionnumber;

import java.util.Calendar;

import hudson.model.Run;

public class BuildsThisMonthGenerator extends AbstractBuildNumberGenerator {

    @Override
    public int resolveValue(Run build, Run prevBuild, int increment) {
        int nextNumber;
        
        // get the current build date and the previous build date
        Calendar curCal = build.getTimestamp();
        Calendar todayCal = prevBuild.getTimestamp();
        
        // get the previous build version number information
        VersionNumberBuildInfo info = getPreviousBuildInfo(prevBuild);

        // increment builds per day
        // increment builds per month
        if (curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
                && curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
            nextNumber = info.getBuildsThisMonth() + increment;
        } else {
            nextNumber = 1;
        }
        
        return nextNumber;
        
    }

}
