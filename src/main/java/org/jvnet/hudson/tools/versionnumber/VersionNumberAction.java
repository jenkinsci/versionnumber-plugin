package org.jvnet.hudson.tools.versionnumber;

import hudson.model.Action;

public class VersionNumberAction implements Action {
    static final String ICON = "/plugin/versionnumber/vnicon_24x24.gif";
    
    private VersionNumberBuildInfo info;
    private String versionNumber;
    
    public VersionNumberAction(VersionNumberBuildInfo info, String versionNumber) {
        this.info = info;
        this.versionNumber = versionNumber;
    }
    
    public VersionNumberBuildInfo getInfo() {
        return info;
    }
    
    public String getDisplayName() {
        return "Version " + this.versionNumber;
    }
    
    public String getIconFileName() {
        return ICON;
    }
    
    public String getUrlName() {
        return "versionnumber/displayName";
    }
    
    
}
