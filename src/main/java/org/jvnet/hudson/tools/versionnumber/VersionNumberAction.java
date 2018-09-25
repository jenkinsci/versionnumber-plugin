package org.jvnet.hudson.tools.versionnumber;

import hudson.model.Action;

public class VersionNumberAction implements Action {
    static final String ICON = "/plugin/versionnumber/vnicon_24x24.gif";
    
    private VersionNumberBuildInfo info;
    private String versionNumber;
    
    private String envPrefix;
    
    public VersionNumberAction(VersionNumberBuildInfo info, String versionNumber, String envPrefix) {
        this.info = info;
        this.versionNumber = versionNumber;
        this.envPrefix = envPrefix;
    }
    
    public VersionNumberBuildInfo getInfo() {
        return info;
    }

    public String getVersionNumber() {
        return this.versionNumber;
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

	public String getEnvPrefix() {
		return envPrefix;
	}

	public void setEnvPrefix(String envPrefix) {
		this.envPrefix = envPrefix;
	}
      
}
