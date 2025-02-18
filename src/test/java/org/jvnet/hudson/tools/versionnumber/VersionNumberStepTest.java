package org.jvnet.hudson.tools.versionnumber;

import hudson.model.Result;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class VersionNumberStepTest {

    @Test
    void versionNumberStep(JenkinsRule j) throws Exception {
        String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber('${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}')
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);

        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b2);
        j.assertBuildStatus(Result.SUCCESS, b2);
        j.assertLogContains("VersionNumber: " + todayDate + "-02", b2);

        WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b3);
        j.assertBuildStatus(Result.SUCCESS, b3);
        j.assertLogContains("VersionNumber: " + todayDate + "-03", b3);
    }

    @Test
    void skipFailedBuildsTrue(JenkinsRule j) throws Exception {
        String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber skipFailedBuilds: true, versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);

        // Force a failure!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber skipFailedBuilds: true, versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        throw new RuntimeException("Fail!")
                        """,
                false));

        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b2);
        j.assertBuildStatus(Result.FAILURE, b2);

        // Restore the working job
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber skipFailedBuilds: true, versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));

        // Make sure we didn't increment!
        WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b3);
        j.assertBuildStatus(Result.SUCCESS, b3);
        j.assertLogContains("VersionNumber: " + todayDate + "-02", b3);
    }

    @Test
    void skipFailedBuildsFalse(JenkinsRule j) throws Exception {
        String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber skipFailedBuilds: false, versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);

        // Force a failure!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber skipFailedBuilds: false, versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        throw new RuntimeException("Fail!")
                        """,
                false));

        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b2);
        j.assertBuildStatus(Result.FAILURE, b2);

        // Restore the working job
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber skipFailedBuilds: false, versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));

        // Make sure that we incremented anyhow!
        WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b3);
        j.assertBuildStatus(Result.SUCCESS, b3);
        j.assertLogContains("VersionNumber: " + todayDate + "-03", b3);
    }

    @Test
    void worstResultForIncrement(JenkinsRule j) throws Exception {
        String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");

        // A working job.
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber worstResultForIncrement: 'SUCCESS', versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);

        // Because the former run succeeded, the following run's build-number will be increased,
        // even though we force a failure!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber worstResultForIncrement: 'SUCCESS', versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        throw new RuntimeException("Fail (worstResultForIncrement: 'SUCCESS')!")
                        """,
                false));
        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b2);
        j.assertBuildStatus(Result.FAILURE, b2);
        j.assertLogContains("VersionNumber: " + todayDate + "-02", b2);

        // Because the former run did fail, the following run's build-number will NOT be increased,
        // regardless of the failure we force again!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber worstResultForIncrement: 'UNSTABLE', versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        throw new RuntimeException("Fail (worstResultForIncrement: 'UNSTABLE')!")
                        """,
                false));
        WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b3);
        j.assertBuildStatus(Result.FAILURE, b3);
        j.assertLogContains("VersionNumber: " + todayDate + "-02", b3);

        // Although the former run did fail, the following run's build-number will be increased,
        // because FAILURE is the worst result which does not prevent incrementing the build-number,
        // regardless of the failure we force again!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber worstResultForIncrement: 'FAILURE', versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        throw new RuntimeException("Fail (worstResultForIncrement: 'FAILURE')!")
                        """,
                false));
        WorkflowRun b4 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b4);
        j.assertBuildStatus(Result.FAILURE, b4);
        j.assertLogContains("VersionNumber: " + todayDate + "-03", b4);

        // Although the former run did fail, the following run's build-number will be increased,
        // because ABORTED is the worst result which does not prevent incrementing the build-number,
        // regardless of the failure we force again!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber worstResultForIncrement: 'ABORTED', versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        throw new RuntimeException("Fail (worstResultForIncrement: 'ABORTED')!")
                        """,
                false));
        WorkflowRun b5 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b5);
        j.assertBuildStatus(Result.FAILURE, b5);
        j.assertLogContains("VersionNumber: " + todayDate + "-04", b5);

        // Although the former run did fail, the following run's build-number will be increased,
        // because ABORTED is the worst result which does not prevent incrementing the build-number,
        // whether we force a failure again or not!
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber worstResultForIncrement: 'ABORTED', versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b6 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b6);
        j.assertBuildStatus(Result.SUCCESS, b6);
        j.assertLogContains("VersionNumber: " + todayDate + "-05", b6);
    }

    @Test
    void versionNumberStepPrefix(JenkinsRule j) throws Exception {
        String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}', versionPrefix: '1.0.'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0." + todayDate + "-01", b1);

        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b2);
        j.assertBuildStatus(Result.SUCCESS, b2);
        j.assertLogContains("VersionNumber: 1.0." + todayDate + "-02", b2);

        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}', versionPrefix: '1.5.'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b3);
        j.assertBuildStatus(Result.SUCCESS, b3);
        j.assertLogContains("VersionNumber: 1.5." + todayDate + "-01", b3);

        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}', versionPrefix: '1.0.'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b4 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b4);
        j.assertBuildStatus(Result.SUCCESS, b4);
        j.assertLogContains("VersionNumber: 1.0." + todayDate + "-03", b4);
    }

    @Test
    void versionNumberStepEnvironment(JenkinsRule j) throws Exception {
        String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        withEnv(['envVar=Hello']) {
                           def versionNumber = VersionNumber('${envVar}-${BUILD_DATE_FORMATTED, "yy-MM-dd"}-${BUILDS_TODAY, XX}')
                           echo "VersionNumber: ${versionNumber}"
                        }
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: Hello-" + todayDate + "-01", b1);
    }

    @Test
    void buildsAllTimeFromEnvironmentVariable(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        withEnv(['NEXT_BUILD_NUMBER=5']) {
                          def versionNumber = VersionNumber versionNumberString: '${BUILDS_ALL_TIME}', versionPrefix: '1.0.', overrideBuildsAllTime: '${NEXT_BUILD_NUMBER}'
                          echo "VersionNumber: ${versionNumber}"
                        }
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.5", b1);
    }

    @Test
    void buildsAllTimeFromEnvironmentVariableThatIsMissing(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILDS_ALL_TIME}', versionPrefix: '1.0.', overrideBuildsAllTime: '${NEXT_BUILD_NUMBER}'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.1", b1);
    }

    @Test
    void buildsAllTimeFromDirectOverride(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILDS_ALL_TIME}', versionPrefix: '1.0.', overrideBuildsAllTime: '12'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.12", b1);
    }

    @Test
    void buildsTodayFromDirectOverride(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILDS_TODAY}', versionPrefix: '1.0.', overrideBuildsToday: '12'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.12", b1);
    }

    @Test
    void buildsThisWeekFromDirectOverride(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILDS_THIS_WEEK}', versionPrefix: '1.0.', overrideBuildsThisWeek: '12'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.12", b1);
    }

    @Test
    void buildsThisMonthFromDirectOverride(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILDS_THIS_MONTH}', versionPrefix: '1.0.', overrideBuildsThisMonth: '12'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.12", b1);
    }

    @Test
    void buildsThisYearFromDirectOverride(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        def versionNumber = VersionNumber versionNumberString: '${BUILDS_THIS_YEAR}', versionPrefix: '1.0.', overrideBuildsThisYear: '12'
                        echo "VersionNumber: ${versionNumber}"
                        """,
                false));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b1);
        j.assertBuildStatus(Result.SUCCESS, b1);
        j.assertLogContains("VersionNumber: 1.0.12", b1);
    }
}
