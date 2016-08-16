package org.jvnet.hudson.tools.versionnumber;

import hudson.model.Action;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility=999)
public class VersionNumberAction implements Action {
    static final String ICON = "/plugin/versionnumber/vnicon_24x24.gif";
    
    private final VersionNumberBuildInfo info;
    private final String versionNumber;
    
    public VersionNumberAction(VersionNumberBuildInfo info, String versionNumber) {
        this.info = info;
        this.versionNumber = versionNumber;
    }
    
    public VersionNumberBuildInfo getInfo() {
        return info;
    }

    @Exported
    public String getVersionNumber() {
	return this.versionNumber;
    }

    @Override
    public String getDisplayName() {
        return "Version " + this.versionNumber;
    }

    @Override
    public String getIconFileName() {
        return ICON;
    }
    
    @Override
    public String getUrlName() {
        return "versionnumber/displayName";
    }
    
    
}
