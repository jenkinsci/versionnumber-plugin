package org.jvnet.hudson.tools.versionnumber;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;

import java.io.File;
import java.io.IOException;

import org.jvnet.hudson.test.HudsonTestCase;

public class VersionNumberBuilderTest extends HudsonTestCase {

	public void testTwoBuilds() throws Exception {
		FreeStyleProject job = createFreeStyleProject("versionNumberJob");
		VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
				"${BUILDS_ALL_TIME}", null, null, null, null, null, null, false);
		job.getBuildWrappersList().add(versionNumberBuilder);
		FreeStyleBuild build = buildAndAssertSuccess(job);
		build = buildAndAssertSuccess(job);
		assertBuildsAllTime(2, build);
	}

	// see #HUDSON-7933
	public void testFailureEarlyDoesNotResetVersionNumber() throws Exception {
		FreeStyleProject job = createFreeStyleProject("versionNumberJob");
		VersionNumberBuilder versionNumberBuilder = new VersionNumberBuilder(
				"${BUILDS_ALL_TIME}", null, null, null, null, null, null, false);
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
