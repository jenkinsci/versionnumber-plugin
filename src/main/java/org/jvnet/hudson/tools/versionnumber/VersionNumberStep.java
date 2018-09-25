/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.tools.versionnumber;

import com.google.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.EnvVars;
import hudson.util.ListBoxModel;

import java.util.Date;
import java.util.logging.Logger;
import java.lang.invoke.MethodHandles;

/**
 * Returns the version number according to the
 * specified version number string.
 *
 * Used like:
 *
 * <pre>
 * def x = VersionNumber("${BUILDS_TODAY}")
 * </pre>
 */
public class VersionNumberStep extends AbstractStepImpl {
    
    /** Use Java 7 MethodHandles to get my class for logger. */
    private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
 
    public final String versionNumberString;

    @DataBoundSetter
    @Deprecated
    public boolean skipFailedBuilds = false;

    @DataBoundSetter
    public String worstResultForIncrement = null;

    @DataBoundSetter
    public String versionPrefix = null;

    @DataBoundSetter
    public String projectStartDate = null;
    
    @DataBoundSetter
    public String overrideBuildsAllTime = null;
    
    @DataBoundSetter
    public String overrideBuildsToday = null;
    
    @DataBoundSetter
    public String overrideBuildsThisWeek = null;
    
    @DataBoundSetter
    public String overrideBuildsThisMonth = null;
    
    @DataBoundSetter
    public String overrideBuildsThisYear = null;
    
    @DataBoundConstructor
    public VersionNumberStep(String versionNumberString) {
        if ((versionNumberString == null) || versionNumberString.isEmpty()) {
            throw new IllegalArgumentException("must specify a version number string.");
        }
        this.versionNumberString = versionNumberString;
    }

    public Date getProjectStartDate() {
        Date value = VersionNumberCommon.parseDate(this.projectStartDate);
        if (value.compareTo(new Date(0)) != 0) {
            return value;
        }
        return null;
    }

    public String getVersionPrefix() {
        if ((this.versionPrefix != null) && (!this.versionPrefix.isEmpty())) {
            return this.versionPrefix;
        }
        return null;
    }

    public Result getWorstResultForIncrement() {
        // For compatibility-reasons during transition from old plugin (<= 1.8.1) to newer plugin.
        if (this.skipFailedBuilds) {
            LOGGER.warning("At least in one project VersionNumber plugin still uses the old config-variable 'skipFailedBuilds'. Make sure to update and safe the job-configs to update that behavior.");
            this.skipFailedBuilds = false;
            this.worstResultForIncrement = VersionNumberCommon.WORST_RESULT_SUCCESS;
        }
        if (this.worstResultForIncrement == null) {
            this.worstResultForIncrement = VersionNumberCommon.WORST_RESULT_NOT_BUILT;
        }
        switch (this.worstResultForIncrement) {
            case VersionNumberCommon.WORST_RESULT_NOT_BUILT:
                return Result.NOT_BUILT;
            case VersionNumberCommon.WORST_RESULT_ABORTED:
                return Result.ABORTED;
            case VersionNumberCommon.WORST_RESULT_FAILURE:
                return Result.FAILURE;
            case VersionNumberCommon.WORST_RESULT_UNSTABLE:
                return Result.UNSTABLE;
            case VersionNumberCommon.WORST_RESULT_SUCCESS:
                return Result.SUCCESS;
            default:
                this.worstResultForIncrement = VersionNumberCommon.WORST_RESULT_SUCCESS;
                return Result.SUCCESS;
        }
    }
    
    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "VersionNumber";
        }

        @Override public String getDisplayName() {
            return "Determine the correct version number";
        }

        public ListBoxModel doFillWorstResultForIncrementItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(VersionNumberCommon.WORST_RESULT_SUCCESS);
            items.add(VersionNumberCommon.WORST_RESULT_UNSTABLE);
            items.add(VersionNumberCommon.WORST_RESULT_FAILURE);
            items.add(VersionNumberCommon.WORST_RESULT_ABORTED);
            items.add(VersionNumberCommon.WORST_RESULT_NOT_BUILT);
            return items;
        }

    }

    public static class Execution extends AbstractSynchronousStepExecution<String> {
        
        @StepContextParameter private transient Run run;
        @StepContextParameter private transient EnvVars env;
        @Inject(optional=true) private transient VersionNumberStep step;

        @Override
        protected String run() throws Exception {
            if (step.versionNumberString != null) {
                try {
                	VersionNumberAction prevAction=null;
                	
                    Run prevBuild = VersionNumberCommon.getPreviousBuildWithVersionNumber(run, step.versionPrefix);
                    VersionNumberBuildInfo info = VersionNumberCommon.incBuild(run, env, prevBuild, step.versionPrefix,  
                            step.getWorstResultForIncrement(),
                            step.overrideBuildsToday,
                            step.overrideBuildsThisWeek,
                            step.overrideBuildsThisMonth,
                            step.overrideBuildsThisYear,
                            step.overrideBuildsAllTime);
                    
                    String formattedVersionNumber = VersionNumberCommon.formatVersionNumber(step.versionNumberString,
                                                                                            step.getProjectStartDate(),
                                                                                            info,
                                                                                            env,
                                                                                            run.getTimestamp());
                    // Difference compared to freestyle jobs.
                    // If a version prefix is specified, it is forced to be prefixed.
                    // Otherwise the version prefix does not function correctly - even in freestyle jobs.
                    // In freestlye jobs it is assumed that the user reuses the version prefix
                    // within the version number string, but this assumption is not documented.
                    // Hence, it might yield to errors, and therefore in pipeline steps, we 
                    // force the version prefix to be prefixed.
                    if (step.versionPrefix != null) {
                        formattedVersionNumber = step.versionPrefix + formattedVersionNumber;
                    }
                    run.addAction(new VersionNumberAction(info, formattedVersionNumber,step.versionPrefix));
                    return formattedVersionNumber;
                } catch (Exception e) {
                }
            }
            return "";
        }

        private static final long serialVersionUID = 2L;

    }

}
