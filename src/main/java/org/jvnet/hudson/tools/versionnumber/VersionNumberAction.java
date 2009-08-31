package org.jvnet.hudson.tools.versionnumber;

import hudson.model.Action;

public class VersionNumberAction implements Action {
	private VersionNumberBuildInfo info;
	
	public VersionNumberAction(VersionNumberBuildInfo info) {
		this.info = info;
	}
	
	public VersionNumberBuildInfo getInfo() {
		return info;
	}

	public String getDisplayName() {
		return "Version Number";
	}

	public String getIconFileName() {
		return null;
	}

	public String getUrlName() {
		return null;
	}

	
}
