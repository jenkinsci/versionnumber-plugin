package org.jvnet.hudson.tools.versionnumber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.NullSCM;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class VersionNumberBuilderTest {

    @Test
    void testIncrementBuildsAllTimeByVersionNumberPrefix(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        FreeStyleBuild build;

        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, "VERSION_PREFIX", null, null, null, null, null, false);

        VersionNumberBuilder versionNumberBuilderWithPrefix = new VersionNumberBuilder(
                "${VERSION_PREFIX}${BUILDS_ALL_TIME}",
                null,
                null,
                "VERSION_PREFIX",
                null,
                null,
                null,
                null,
                null,
                false);

        job.getBuildWrappersList().add(versionNumberBuilder);

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(1, build);

        build = r.buildAndAssertSuccess(job); // The pull request #2 (by @mranostay) causes a null pointer here.
        assertBuildsAllTime(2, build);

        job.getBuildWrappersList().clear();
        job.getBuildWrappersList().add(versionNumberBuilderWithPrefix);

        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        r.jenkins.getGlobalNodeProperties().add(prop);

        envVars.put("VERSION_PREFIX", "2.0.");

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(1, build);

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(2, build);

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(3, build);

        envVars.put("VERSION_PREFIX", "2.5.");

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(1, build);

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(2, build);

        envVars.put("VERSION_PREFIX", "2.0.");

        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(4, build);
    }

    @Test
    void testTwoBuilds(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, null, false);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(2, build);
    }

    // see #HUDSON-7933
    @Test
    void testFailureEarlyDoesNotResetVersionNumber(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, null, false);
        job.getBuildWrappersList().add(versionNumberBuilder);
        r.buildAndAssertSuccess(job);
        r.buildAndAssertSuccess(job);
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(3, build);
        job.setScm(new FailureSCM());
        build = job.scheduleBuild2(0).get();
        r.assertBuildStatus(Result.FAILURE, build);
        // When build fails very early, there will be no VersionNumber attached
        // to it.
        assertNull(build.getAction(VersionNumberAction.class));
        job.setScm(new NullSCM());
        build = r.buildAndAssertSuccess(job);
        assertBuildsAllTime(4, build);
    }

    @Test
    void testUseAsBuildDisplayName(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "1.0.${BUILDS_ALL_TIME}", null, null, null, null, null, null, null, null, false, true);
        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        assertEquals("1.0.1", build.getDisplayName());
        build = r.buildAndAssertSuccess(job);
        assertEquals("1.0.2", build.getDisplayName());
    }

    @Test
    void testValueFromEnvironmentVariable(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "${BUILDS_TODAY}.${BUILDS_THIS_WEEK}.${BUILDS_THIS_MONTH}.${BUILDS_THIS_YEAR}.${BUILDS_ALL_TIME}",
                null,
                null,
                null,
                "${ENVVAL_OF_TODAY}",
                "${ENVVAL_OF_THIS_WEEK}",
                "${ENVVAL_OF_THIS_MONTH}",
                "${ENVVAL_OF_THIS_YEAR}",
                "${ENVVAL_OF_ALL_TIME}",
                false,
                true);

        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("ENVVAL_OF_TODAY", "-10"); // Invalid (negative) value
        envVars.put("ENVVAL_OF_THIS_WEEK", "2.0"); // Invalid (float number) value
        envVars.put("ENVVAL_OF_THIS_MONTH", "Invalid"); // Invalid (non-number) value
        // envVars.put("ENVVAL_OF_THIS_YEAR", "");        // No variable
        envVars.put("ENVVAL_OF_ALL_TIME", "20"); // Normal value
        r.jenkins.getGlobalNodeProperties().add(prop);

        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        assertEquals("1.1.1.1.20", build.getDisplayName());

        // make sure that the overrides via environment variables remain
        assertEquals("${ENVVAL_OF_ALL_TIME}", versionNumberBuilder.getBuildsAllTime());
        assertEquals("${ENVVAL_OF_TODAY}", versionNumberBuilder.getBuildsToday());
        assertEquals("${ENVVAL_OF_THIS_WEEK}", versionNumberBuilder.getBuildsThisWeek());
        assertEquals("${ENVVAL_OF_THIS_MONTH}", versionNumberBuilder.getBuildsThisMonth());
        assertEquals("${ENVVAL_OF_THIS_YEAR}", versionNumberBuilder.getBuildsThisYear());
    }

    @Test
    void testThatOverrideValuesGetResetAfterJobRuns(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "${BUILDS_TODAY}.${BUILDS_THIS_WEEK}.${BUILDS_THIS_MONTH}.${BUILDS_THIS_YEAR}.${BUILDS_ALL_TIME}",
                null,
                null,
                null,
                "1",
                "7",
                "30",
                "365",
                "500",
                false,
                true);

        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        r.jenkins.getGlobalNodeProperties().add(prop);

        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        assertEquals("1.7.30.365.500", build.getDisplayName());

        // make sure that the direct set variables were cleaned up
        assertEquals("", versionNumberBuilder.getBuildsAllTime());
        assertEquals("", versionNumberBuilder.getBuildsToday());
        assertEquals("", versionNumberBuilder.getBuildsThisWeek());
        assertEquals("", versionNumberBuilder.getBuildsThisMonth());
        assertEquals("", versionNumberBuilder.getBuildsThisYear());
    }

    @Test
    void testSubstringFromEnvironmentVariable(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("versionNumberJob");
        VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
                "${ENVVAL_TESTING, \"+3\"}.${ENVVAL_TESTING, \"2\"}.${ENVVAL_TESTING, \"-3\"}"
                        + ".${ENVVAL_TESTING, \"5\"}.${ENVVAL_TESTING, \"+5\"}.${ENVVAL_TESTING, \"-5\"}"
                        + ".${ENVVAL_TESTING, \"0\"}.${ENVVAL_TESTING, \"1.5\"}.${ENVVAL_TESTING, \"test\"}",
                null,
                "${ENVVAL_TESTING}",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                true);
        // tried
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("ENVVAL_TESTING", "1234");
        r.jenkins.getGlobalNodeProperties().add(prop);

        job.getBuildWrappersList().add(versionNumberBuilder);
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        assertEquals("123.12.234.1234.1234.1234.1234.1234.1234", build.getDisplayName());
    }

    private static void assertBuildsAllTime(int expected, AbstractBuild build) {
        VersionNumberAction versionNumberAction = build.getAction(VersionNumberAction.class);
        assertEquals(expected, versionNumberAction.getInfo().getBuildsAllTime());
    }

    private static class FailureSCM extends NullSCM {
        @Override
        @Deprecated
        public boolean checkout(
                AbstractBuild<?, ?> build,
                Launcher launcher,
                FilePath remoteDir,
                BuildListener listener,
                @NonNull File changeLogFile) {
            return false;
        }
    }
}
