/*
 * The MIT License
 *
 * Copyright (c) 2009, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.PluginImpl;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Defines a new {@link ParameterDefinition} to be displayed at the top of the
 * configuration page of {@link AbstractProject}s.
 *
 * <p>For this parameter to actually work, it is mandatory for the {@link AbstractProject}s
 * using it to set their SCM to be {@link ClearCaseUcmBaselineSCM}. If not set,
 * the build will be aborted (see {@link ClearCaseUcmBaselineParameterValue#createBuildWrapper}
 * which does this check).</p>
 *
 * <p>When used, this parameter will request the user to select a ClearCase UCM
 * baseline at run-time by displaying a drop-down list. See {@link ClearCaseUcmBaselineParameterValue}.
 * </p>
 *
 * <p>This parameter consists in a set of attributes to be set at config-time:<ul>
 * <li>The ClearCase UCM PVOB name;</li>
 * <li>The ClearCase UCM VOB name;</li>
 * <li>The ClearCase UCM component name;</li>
 * <li>The ClearCase UCM promotion level (e.g. RELEASED);</li>
 * <li>The ClearCase UCM view to create name.</li>
 * </ul></p>
 * <p>The following attribute is then asked at run-time:<ul>
 * <li>The ClearCase UCM baseline.</li>
 * </ul></p>
 *
 * @author Romain Seguy (http://davadoc.deviantart.com)
 * @version 1.0
 */
public class ClearCaseUcmBaselineParameterDefinition extends ParameterDefinition {

    public final static String PARAMETER_NAME = "ClearCase UCM baseline";

    private final String component;
    private final String pvob;
    private final String viewName;
    private final String vob;
    /**
     * The promotion level is optional: If not is set, then the user will be
     * offered with all the baselines of the ClearCase UCM component.
     */
    private final String promotionLevel;

    @DataBoundConstructor
    public ClearCaseUcmBaselineParameterDefinition(String pvob, String vob, String component, String promotionLevel, String viewName) {
        super(PARAMETER_NAME); // we keep the name of the parameter not
                                         // internationalized, it will save many
                                         // issues when updating system settings
        this.pvob = pvob;
        this.vob = vob;
        this.component = component;
        this.promotionLevel = promotionLevel;
        this.viewName = viewName;
    }

    // This method is invoked from a GET or POST HTTP request
    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        if(values == null || values.length != 1) {
            // the "ClearCase UCM baseline" parameter is mandatory, the build
            // has to fail if it's not there (we can't assume a default value)
            return null;
        }
        else {
            return new ClearCaseUcmBaselineParameterValue(
                    getName(), getPvob(), getVob(), getComponent(), getPromotionLevel(), getViewName(), values[0]);
        }
    }

    // This method is invoked when the user clicks on the "Build" button of Hudon's GUI
    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
        ClearCaseUcmBaselineParameterValue value = req.bindJSON(ClearCaseUcmBaselineParameterValue.class, formData);

        value.setPvob(pvob);
        value.setVob(vob);
        value.setComponent(component);
        value.setPromotionLevel(promotionLevel);
        value.setViewName(viewName);

        return value;
    }

    /**
     * Returns an array of ClearCase UCM baselines to be displayed in
     * {@code ClearCaseUcmBaselineParameterDefinition/index.jelly} (or {@code null}
     * if something wrong happens).
     */
    public String[] getBaselines() throws IOException, InterruptedException {
        // cleartool lsbl -fmt "%[name]p " -level <promotion level> -component <component>@<vob>
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(PluginImpl.getDescriptor().getCleartoolExe());
        cmd.add("lsbl");
        cmd.add("-fmt");
        cmd.add("%[name]p ");
        if(promotionLevel != null && promotionLevel.length() > 0) {
            cmd.add("-level");
            cmd.add(promotionLevel);
        }
        cmd.add("-component");
        cmd.add(component + '@' + pvob);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hudson.getInstance().createLauncher(TaskListener.NULL).launch().cmds(cmd).stdout(baos).join();
        String cleartoolOutput = ClearCaseUcmBaselineUtils.processCleartoolOuput(baos);
        baos.close();

        if(cleartoolOutput.toString().contains("cleartool: Error")) {
            return null;
        }
        else {
            return cleartoolOutput.toString().split(" ");
        }
    }

    public String getComponent() {
        return component;
    }

    public String getPromotionLevel() {
        return promotionLevel;
    }

    public String getPvob() {
        return pvob;
    }

    public String getViewName() {
        return viewName;
    }

    public String getVob() {
        return vob;
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        public FormValidation doCheckComponent(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("ComponentMustBeSet"));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckPromotionLevel(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.warning(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("PromotionLevelShouldBeSet"));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckPvob(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("PVOBMustBeSet"));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckViewName(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("ViewNameMustBeSet"));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckVob(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("VOBMustBeSet"));
            }

            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("DisplayName");
        }

    }

}
