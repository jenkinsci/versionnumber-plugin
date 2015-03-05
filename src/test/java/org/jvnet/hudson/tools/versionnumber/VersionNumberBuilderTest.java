package org.jvnet.hudson.tools.versionnumber;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import java.io.File;
import java.io.IOException;

import org.jvnet.hudson.test.HudsonTestCase;

public class VersionNumberBuilderTest extends HudsonTestCase {

    public void testIncrementBuildsAllTimeByVersionNumberPrefix() throws Exception {
        
        FreeStyleProject job = super.createFreeStyleProject("versionNumberJob");
        FreeStyleBuild build;
        
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, "VERSION_PREFIX", null, null, null, null, false);
        
        VersionNumberBuilder versionNumberBuilderWithPrefix = new VersionNumberBuilder(
                "${VERSION_PREFIX}${BUILDS_ALL_TIME}", null, null, "VERSION_PREFIX", null, null, null, null, false);
        
        job.getBuildWrappersList().add(versionNumberBuilder);
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(1, build);
        
        build = super.buildAndAssertSuccess(job);  // The pull request #2 (by @mranostay) causes a null pointer here.
        this.assertBuildsAllTime(2, build);

        job.getBuildWrappersList().clear();
        job.getBuildWrappersList().add(versionNumberBuilderWithPrefix);
        
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        super.hudson.getGlobalNodeProperties().add(prop);
        
        envVars.put("VERSION_PREFIX", "2.0.");
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(1, build);
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(2, build);
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(3, build);
        
        envVars.put("VERSION_PREFIX", "2.5.");
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(1, build);
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(2, build);
        
        envVars.put("VERSION_PREFIX", "2.0.");
        
        build = super.buildAndAssertSuccess(job);
        this.assertBuildsAllTime(4, build);
    }
    
    public void testTwoBuilds() throws Exception {
        FreeStyleProject job = createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, false);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = buildAndAssertSuccess(job);
        build = buildAndAssertSuccess(job);
        assertBuildsAllTime(2, build);
    }

    // see #HUDSON-7933
    public void testFailureEarlyDoesNotResetVersionNumber() throws Exception {
        FreeStyleProject job = createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, false);
        job.getBuildWrappersList().add(versionNumberBuilder);
        buildAndAssertSuccess(job);
        buildAndAssertSuccess(job);
        FreeStyleBuild build = buildAndAssertSuccess(job);
        assertBuildsAllTime(3, build);
        job.setScm(new FailureSCM());
        build = job.scheduleBuild2(0).get();
        assertBuildStatus(Result.FAILURE, build);
        // When build fails very early, there will be no VersionNumber attached
        // to it. 
        assertNull(build.getAction(VersionNumberAction.class));
        job.setScm(new NullSCM());
        build = buildAndAssertSuccess(job);
        assertBuildsAllTime(4, build);
    }

    public void testUseAsBuildDisplayName() throws Exception {
        FreeStyleProject job = createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, false, true);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = buildAndAssertSuccess(job);
        assertEquals("1.0.1", build.getDisplayName());
        build = buildAndAssertSuccess(job);
        assertEquals("1.0.2", build.getDisplayName());
    }
    
    private void assertBuildsAllTime(int expected, AbstractBuild build) {
        VersionNumberAction versionNumberAction = build
                .getAction(VersionNumberAction.class);
        assertEquals(expected, versionNumberAction.getInfo().getBuildsAllTime());
    }

    private static class FailureSCM extends NullSCM {

        @Override
        public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
                FilePath remoteDir, BuildListener listener, File changeLogFile)
                throws IOException, InterruptedException {
            return false;
        }

    }

}
