/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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

package com.michelin.cio.hudson.plugins.clearcaseucmbaseline;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.AbstractClearCaseScm;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.io.File;
import java.io.IOException;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * This class is of no real use and is only there for a "ClearCase UCM baseline"
 * option to be displayed in the "Source Code Management" section of the
 * configuration screen of {@link AbstractProject}s.
 *
 * <p>If you refer to {@code ClearCaseUcmBaselineSCM/config.jelly} file, you'll
 * see that there is no possibility to configure this option: This option is just
 * there so that the user is displayed with a message saying that, if he wants
 * to gets his data from a ClearCase UBM baseline, he has to actually add a
 * "ClearCase UCM baseline" build parameter to get the real configuration fields
 * (as defined in {@link ClearCaseUcmBaselineParameterDefinition}).</p>
 *
 * @see ClearCaseUcmBaselineParameterDefinition
 *
 * @author Romain Seguy (http://davadoc.deviantart.com)
 */
public class ClearCaseUcmBaselineSCM extends SCM {

    public final static String CLEARCASE_BASELINE_ENVSTR = "CLEARCASE_BASELINE";

    @DataBoundConstructor
    public ClearCaseUcmBaselineSCM() {
    }

    /**
     * This method does nothing except checking that a "ClearCase UCM baseline"
     * parameter has been defined for the job (it not, the build will fail).
     */
    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        // has the project some parameters?
        ParametersDefinitionProperty params = (ParametersDefinitionProperty) build.getProject().getProperty(ParametersDefinitionProperty.class);
        if(params == null) {
            listener.fatalError("No parameters have been defined for this project: To use the ClearCase UCM baseline mode, you MUST add a '"
                    + ClearCaseUcmBaselineParameterDefinition.PARAMETER_NAME
                    + "' parameter to the project.");
            return false;
        }

        // let's count the number of CC UCM baseline paramaters for the build:
        // if we have more than 1, we'll make the job fail to avoid any issues
        // with the environment variables
        int clearCaseUcmBaselineParameters = 0;
        for(ParameterDefinition pd : params.getParameterDefinitions()) {
            if(pd.getName().equals(ClearCaseUcmBaselineParameterDefinition.PARAMETER_NAME)) {
                if(!(pd instanceof ClearCaseUcmBaselineParameterDefinition)) {
                    // the parameter with the name ClearCase UCM baseline is NOT
                    // a real ClearCaseUcmBaselineParameterDefinition, we make
                    // the build fail
                    listener.fatalError("A parameter named '"
                            + ClearCaseUcmBaselineParameterDefinition.PARAMETER_NAME
                            + "' doesn't correspond to what's expected by "
                            + Hudson.getInstance().getDescriptor(ClearCaseUcmBaselineSCM.class).getDisplayName()
                            + " (e.g. you may have added a String parameter named '"
                            + ClearCaseUcmBaselineParameterDefinition.PARAMETER_NAME
                            + "'); Remove it from the project configuration before trying to run the build again.");
                    return false;
                }
                clearCaseUcmBaselineParameters++;
            }
        }
        if(clearCaseUcmBaselineParameters > 1) {
            listener.fatalError("More than two '"
                    + ClearCaseUcmBaselineParameterDefinition.PARAMETER_NAME
                    + "' parameters have been defined for this project: Aborting the build to avoid any conflict with the "
                    + AbstractClearCaseScm.CLEARCASE_VIEWNAME_ENVSTR
                    + " and "
                    + AbstractClearCaseScm.CLEARCASE_VIEWPATH_ENVSTR
                    + " environment variables; Edit the project configuration and remove one of them before trying to run the build again.");
            return false;
        }

        // everything is fine (well, for the moment)
        return true;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        return false;
    }

    @Extension
    public static class DescriptorImpl extends SCMDescriptor<ClearCaseUcmBaselineSCM> {

        public DescriptorImpl() {
            super(ClearCaseUcmBaselineSCM.class, null);
            load();
        }

        @Override
        public String getDisplayName() {
            return ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("DisplayName");
        }

        @Override
        public SCM newInstance(StaplerRequest req) throws FormException {
            return new ClearCaseUcmBaselineSCM();
        }

    }

}
