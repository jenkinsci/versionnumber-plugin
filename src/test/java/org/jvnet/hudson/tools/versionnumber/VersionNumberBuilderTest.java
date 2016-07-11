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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.jvnet.hudson.test.HudsonTestCase;

public class VersionNumberBuilderTest extends HudsonTestCase {

    public void testIncrementBuildsAllTimeByVersionNumberPrefix() throws Exception {
        
        FreeStyleProject job = super.createFreeStyleProject("versionNumberJob");
        FreeStyleBuild build;
        
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, "VERSION_PREFIX", null, null, null, null, null, false);
        
        VersionNumberBuilder versionNumberBuilderWithPrefix = new VersionNumberBuilder(
                "${VERSION_PREFIX}${BUILDS_ALL_TIME}", null, null, "VERSION_PREFIX", null, null, null, null, null, false);
        
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
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, null, false);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = buildAndAssertSuccess(job);
        build = buildAndAssertSuccess(job);
        assertBuildsAllTime(2, build);
    }

    // see #HUDSON-7933
    public void testFailureEarlyDoesNotResetVersionNumber() throws Exception {
        FreeStyleProject job = createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, null, false);
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
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, null, false, true, false);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = buildAndAssertSuccess(job);
        assertEquals("1.0.1", build.getDisplayName());
        build = buildAndAssertSuccess(job);
        assertEquals("1.0.2", build.getDisplayName());
    }

    public void testUseUtcTimeZone() throws Exception {
        String dateFormatPattern = "yyyyMMddHH";
        FreeStyleProject job = createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "${BUILD_DATE_FORMATTED, \"" + dateFormatPattern + "\"}", null, null, null, null, null, null, null, null, false, true, true);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = buildAndAssertSuccess(job);

        SimpleDateFormat format = new SimpleDateFormat(dateFormatPattern);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedBuildTimestamp = format.format(build.getTimestamp().getTime());
        assertEquals(formattedBuildTimestamp, build.getDisplayName());
    }
    
    public void testValueFromEnvironmentVariable() throws Exception {
        FreeStyleProject job = createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "${BUILDS_TODAY}.${BUILDS_THIS_WEEK}.${BUILDS_THIS_MONTH}.${BUILDS_THIS_YEAR}.${BUILDS_ALL_TIME}",
                null, null, null, "${ENVVAL_OF_TODAY}", "${ENVVAL_OF_THIS_WEEK}", "${ENVVAL_OF_THIS_MONTH}", "${ENVVAL_OF_THIS_YEAR}", "${ENVVAL_OF_ALL_TIME}", false, true, false);
        
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("ENVVAL_OF_TODAY", "-10");           // Invalid (negative) value
        envVars.put("ENVVAL_OF_THIS_WEEK", "2.0");       // Invalid (float number) value
        envVars.put("ENVVAL_OF_THIS_MONTH", "Invalid");  // Invalid (non-number) value
        //envVars.put("ENVVAL_OF_THIS_YEAR", "");        // No variable
        envVars.put("ENVVAL_OF_ALL_TIME", "20");         // Normal value
        super.hudson.getGlobalNodeProperties().add(prop);
        
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = buildAndAssertSuccess(job);
        assertEquals("1.1.1.1.20", build.getDisplayName());
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
