package org.jvnet.hudson.tools.versionnumber;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import hudson.model.Result;

public class VersionNumberStepTest {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void VersionNumberStep() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber('${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}')\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);
                
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b2);
                story.j.assertBuildStatus(Result.SUCCESS, b2);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-02", b2);
                
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b3);
                story.j.assertBuildStatus(Result.SUCCESS, b3);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-03", b3);
            }
        });
    }
    
    @Test
    public void SkipFailedBuildsTrue() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber skipFailedBuilds: true, versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);

                // Force a failure!
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber skipFailedBuilds: true, versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}'\n" +
                        "throw new RuntimeException(\"Fail!\")"
                ));
                
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b2);
                story.j.assertBuildStatus(Result.FAILURE, b2);

                // Restore the working job
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber skipFailedBuilds: true, versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                
                // Make sure we didn't increment!
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b3);
                story.j.assertBuildStatus(Result.SUCCESS, b3);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-02", b3);
            }
        });
        
    }
    
    @Test
    public void SkipFailedBuildsFalse() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber skipFailedBuilds: false, versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-01", b1);

                // Force a failure!
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber skipFailedBuilds: false, versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}'\n" +
                        "throw new RuntimeException(\"Fail!\")"
                ));
                
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b2);
                story.j.assertBuildStatus(Result.FAILURE, b2);

                // Restore the working job
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber skipFailedBuilds: false, versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                
                // Make sure that we incremented anyhow!
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b3);
                story.j.assertBuildStatus(Result.SUCCESS, b3);
                story.j.assertLogContains("VersionNumber: " + todayDate + "-03", b3);
            }
        });
        
    }
    
    @Test
    public void VersionNumberStepPrefix() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}', versionPrefix: '1.0.'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0." + todayDate + "-01", b1);
                
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b2);
                story.j.assertBuildStatus(Result.SUCCESS, b2);
                story.j.assertLogContains("VersionNumber: 1.0." + todayDate + "-02", b2);
                
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}', versionPrefix: '1.5.'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b3);
                story.j.assertBuildStatus(Result.SUCCESS, b3);
                story.j.assertLogContains("VersionNumber: 1.5." + todayDate + "-01", b3);
                
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}', versionPrefix: '1.0.'\n" +
                        "echo \"VersionNumber: ${versionNumber}\""
                ));
                WorkflowRun b4 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b4);
                story.j.assertBuildStatus(Result.SUCCESS, b4);
                story.j.assertLogContains("VersionNumber: 1.0." + todayDate + "-03", b4);
            }
        });
    }
    
    @Test
    public void VersionNumberStepEnvironment() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String todayDate = new SimpleDateFormat("yy-MM-dd").format(new Date());
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "withEnv(['envVar=Hello']) {\n" +
                        "   def versionNumber = VersionNumber('${envVar}-${BUILD_DATE_FORMATTED, \"yy-MM-dd\"}-${BUILDS_TODAY, XX}')\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n" +
                        "}"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: Hello-" + todayDate + "-01", b1);
            }
        });
    }
    
    @Test
    public void BuildsAllTimeFromEnvironmentVariable() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "withEnv(['NEXT_BUILD_NUMBER=5']) {\n" +
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_ALL_TIME}', versionPrefix: '1.0.', overrideBuildsAllTime: '${NEXT_BUILD_NUMBER}'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n" +
                        "}"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.5", b1);
            }
        });
    }

    @Test
    public void BuildsAllTimeFromEnvironmentVariableThatIsMissing() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_ALL_TIME}', versionPrefix: '1.0.', overrideBuildsAllTime: '${NEXT_BUILD_NUMBER}'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.1", b1);
            }
        });
    }
    
    @Test
    public void BuildsAllTimeFromDirectOverride() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_ALL_TIME}', versionPrefix: '1.0.', overrideBuildsAllTime: '12'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.12", b1);
            }
        });
    }

    @Test
    public void BuildsTodayFromDirectOverride() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_TODAY}', versionPrefix: '1.0.', overrideBuildsToday: '12'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.12", b1);
            }
        });
    }

    @Test
    public void BuildsThisWeekFromDirectOverride() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_THIS_WEEK}', versionPrefix: '1.0.', overrideBuildsThisWeek: '12'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.12", b1);
            }
        });
    }
    
    @Test
    public void BuildsThisMonthFromDirectOverride() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_THIS_MONTH}', versionPrefix: '1.0.', overrideBuildsThisMonth: '12'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.12", b1);
            }
        });
    }

    @Test
    public void BuildsThisYearFromDirectOverride() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def versionNumber = VersionNumber versionNumberString: '${BUILDS_THIS_YEAR}', versionPrefix: '1.0.', overrideBuildsThisYear: '12'\n" +
                        "   echo \"VersionNumber: ${versionNumber}\"\n"
                ));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                story.j.waitForCompletion(b1);
                story.j.assertBuildStatus(Result.SUCCESS, b1);
                story.j.assertLogContains("VersionNumber: 1.0.12", b1);
            }
        });
    }
    
}
