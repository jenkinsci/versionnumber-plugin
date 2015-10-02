package org.jvnet.hudson.tools.versionnumber;

public class VersionNumberBuildInfo {
    private int buildsToday;
    private int buildsThisWeek;
    private int buildsThisMonth;
    private int buildsThisYear;
    private int buildsAllTime;
    
    public VersionNumberBuildInfo(int buildsToday, int buildsThisWeek, int buildsThisMonth,
                                  int buildsThisYear, int buildsAllTime) {
        super();
        this.buildsToday = buildsToday;
        this.buildsThisWeek = buildsThisWeek;
        this.buildsThisMonth = buildsThisMonth;
        this.buildsThisYear = buildsThisYear;
        this.buildsAllTime = buildsAllTime;
    }
    
    public int getBuildsToday() {
        return buildsToday;
    }
    public int getBuildsThisWeek() {
        return buildsThisWeek;
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
