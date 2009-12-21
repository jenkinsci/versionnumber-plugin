package org.jvnet.hudson.tools.versionnumber;

public class VersionNumberBuildInfo {
    private int buildsToday;
    private int buildsThisMonth;
    private int buildsThisYear;
    private int buildsAllTime;
    
    public VersionNumberBuildInfo(int buildsToday, int buildsThisMonth,
                                  int buildsThisYear, int buildsAllTime) {
        super();
        this.buildsToday = buildsToday;
        this.buildsThisMonth = buildsThisMonth;
        this.buildsThisYear = buildsThisYear;
        this.buildsAllTime = buildsAllTime;
    }
    
    public int getBuildsToday() {
        return buildsToday;
    }
    public int getBuildsThisMonth() {
        return buildsThisMonth;
    }
    public int getBuildsThisYear() {
        return buildsThisYear;
    }
    public int getBuildsAllTime() {
        return buildsAllTime;
    }
}
