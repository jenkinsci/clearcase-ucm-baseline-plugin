/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Romain Seguy
 * Copyright (c) 2007-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 *                          Henrik Lynggaard, Peter Liljenberg, Andrew Bayer
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

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.AbstractClearCaseScm;
import hudson.plugins.clearcase.ClearToolLauncher;
import hudson.plugins.clearcase.HudsonClearToolLauncher;
import hudson.plugins.clearcase.PluginImpl;
import hudson.plugins.clearcase.util.BuildVariableResolver;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * This class represents the actual {@link ParameterValue} for the
 * {@link ClearCaseUcmBaselineParameterDefinition} parameter.
 *
 * <p>This value holds the following information:
 * <li>The ClearCase UCM PVOB name (which is actually defined at config-time &mdash;
 * cf. {@link ClearCaseUcmBaselineParameterDefinition});</li>
 * <li>The ClearCase UCM VOB name (which is actually defined at config-time &mdash;
 * cf. {@link ClearCaseUcmBaselineParameterDefinition});</li>
 * <li>The ClearCase UCM component name (which is actually defined at config-time
 * &mdash; cf. {@link ClearCaseUcmBaselineParameterDefinition});</li>
 * <li>The ClearCase UCM promotion level (which is actually defined at config-time
 * &mdash; cf. {@link ClearCaseUcmBaselineParameterDefinition});</li>
 * <li>The ClearCase UCM view to create name (which is actually defined at config-time
 * &mdash; cf. {@link ClearCaseUcmBaselineParameterDefinition});</li>
 * <li>The ClearCase UCM baseline (this is the only one information which is
 * asked at build-time)</li>
 * </ul></p>
 *
 * @author Romain Seguy (http://davadoc.deviantart.com)
 */
public class ClearCaseUcmBaselineParameterValue extends ParameterValue {

    @Exported(visibility=3) private String baseline;        // this att is set by the user once the build takes place
    @Exported(visibility=3) private String component;       // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private String promotionLevel;  // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private String pvob;            // this att comes from ClearCaseUcmBaselineParameterDefinition
    private List<String> restrictions;
    @Exported(visibility=3) private String viewName;        // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private String vob;             // this att comes from ClearCaseUcmBaselineParameterDefinition
    private String normalizedViewName;

    @DataBoundConstructor
    public ClearCaseUcmBaselineParameterValue(String name, String pvob, String vob, String component, String promotionLevel, String viewName, String baseline) {
        super(name);
        this.pvob = pvob;
        this.vob = vob;
        this.component = component;
        this.promotionLevel = promotionLevel;
        this.viewName = viewName;
        this.baseline = baseline;
    }

    /**
     * Returns the {@link BuildWrapper} (defined as an inner class) which does
     * the "checkout" from the ClearCase UCM baseline selected by the user.
     *
     * <p>If a {@link ClearCaseUcmBaselineParameterDefinition} is added for the
     * build but the SCM is not {@link ClearCaseUcmBaselineSCM}, then the
     * {@link BuildWrapper} which is returned will make the build fail.</p>
     */
    @Override
    public BuildWrapper createBuildWrapper(AbstractBuild<?, ?> build) {
        // let's ensure that a baseline has been really provided
        if(baseline == null || baseline.length() == 0) {
            return new BuildWrapper() {
                /**
                 * This method makes the build fail when baseline value is {@code
                 * null} or empty.
                 */
                @Override
                public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                    listener.fatalError("The value '" + baseline + "' is not a valid ClearCase UCM baseline.");
                    return null;
                }
            };
        }

        if(build.getProject().getScm() instanceof ClearCaseUcmBaselineSCM) {
            // the job is apparently set-up in a clean way, the build can take place
            return new BuildWrapper() {

                private ClearToolLauncher createClearToolLauncher(TaskListener listener, FilePath workspace, Launcher launcher) {
                    return new HudsonClearToolLauncher(
                            PluginImpl.getDescriptor().getCleartoolExe(),
                            Hudson.getInstance().getDescriptor(ClearCaseUcmBaselineSCM.class).getDisplayName(),
                            listener,
                            workspace,
                            launcher);
                }

                /**
                 * This method is a copy of {@link AbstractClearCaseScm#generateNormalizedViewName}
                 * which is unfortunately not static.
                 *
                 * @see AbstractClearCaseScm#generateNormalizedViewName
                 */
                private String generateNormalizedViewName(VariableResolver variableResolver, String viewName) {
                    String normalizedViewName = Util.replaceMacro(viewName, variableResolver);
                    normalizedViewName = normalizedViewName.replaceAll("[\\s\\\\\\/:\\?\\*\\|]+", "_");
                    return normalizedViewName;
                }

                /**
                 * This method is the one which actually does the ClearCase stuff
                 * (creating the view, setting the config spec, downloading the
                 * view, etc.).
                 *
                 * <p>This method is invoked when the user clicks on the 'Build'
                 * button appearing on the parameters page.</p>
                 */
                @Override
                public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                    VariableResolver variableResolver = new BuildVariableResolver(build, launcher);
                    ClearToolLauncher clearToolLauncher = createClearToolLauncher(listener, build.getProject().getWorkspace(), launcher);
                    ClearToolUcmBaseline cleartool = new ClearToolUcmBaseline(variableResolver, clearToolLauncher);

                    normalizedViewName = generateNormalizedViewName(variableResolver, viewName);

                    FilePath workspace = build.getProject().getWorkspace();
                    FilePath viewPath = workspace.child(normalizedViewName);
                    String rootDir = '/' + vob + '/' + component;
                    StringBuilder configSpec = new StringBuilder();

                    // TODO Don't create the view if it already exists

                    // --- 1. We remove the view if it already exists ---

                    if(viewPath.exists()) {
                        cleartool.rmview(normalizedViewName);
                    }

                    // --- 2. We first create the view to be loaded ---

                    // cleartool mkview -tag <tag> <view path>
                    cleartool.mkview(normalizedViewName, null);

                    // --- 3. We create the configspec ---

                    configSpec.append("element * CHECKEDOUT\n");
                    configSpec.append("element ").append(rootDir).append("/... ").append(baseline).append('\n');

                    // cleartool lsbl -fmt "%[depends_on_closure]p" <baseline>@<vob>
                    String[] dependentBaselines = cleartool.getDependentBaselines(pvob, baseline);

                    for(String dependentBaselineSelector: dependentBaselines) {
                        String dependentBaseline = dependentBaselineSelector.split("@")[0];
                        String component = cleartool.getComponentFromBaseline(pvob, dependentBaseline);
                        rootDir = cleartool.getComponentRootDir(pvob, component);
                        configSpec.append("element ").append(rootDir).append("/... ").append(dependentBaseline).append('\n');
                    }

                    configSpec.append("element * /main/0\n");

                    // is any download restriction defined?
                    if(restrictions != null && restrictions.size() > 0) {
                        for(String restriction: restrictions) {
                            if(restriction.startsWith(rootDir)) {
                                configSpec.append("load ").append(restriction).append('\n');
                            }
                        }
                    }
                    else {
                        configSpec.append("load ").append(rootDir).append('\n');
                    }

                    for(String dependentBaselineSelector: dependentBaselines) {
                        String dependentBaseline = dependentBaselineSelector.split("@")[0];
                        String component = cleartool.getComponentFromBaseline(pvob, dependentBaseline);
                        rootDir = cleartool.getComponentRootDir(pvob, component);

                        // is any download restriction defined?
                        if(restrictions != null && restrictions.size() > 0) {
                            for(String restriction: restrictions) {
                                if(restriction.startsWith(rootDir)) {
                                    configSpec.append("load ").append(restriction).append('\n');
                                }
                            }
                        }
                        else {
                            configSpec.append("load ").append(rootDir).append('\n');
                        }
                    }

                    listener.getLogger().println("The view will be created based on the following config spec:");
                    listener.getLogger().println("--- config spec start ---");
                    listener.getLogger().print(configSpec.toString());
                    listener.getLogger().println("---  config spec end  ---");

                    // --- 4. We actually load the view based on the configspec ---

                    // cleartool setcs <configspec>
                    cleartool.setcs(normalizedViewName, configSpec.toString());

                    // --- 5. Create the environment variables ---

                    return new Environment() {
                        @Override
                        public void buildEnvVars(Map<String, String> env) {
                            env.put(ClearCaseUcmBaselineSCM.CLEARCASE_BASELINE_ENVSTR,
                                    baseline);
                            env.put(AbstractClearCaseScm.CLEARCASE_VIEWNAME_ENVSTR,
                                    normalizedViewName);
                            env.put(AbstractClearCaseScm.CLEARCASE_VIEWPATH_ENVSTR,
                                    env.get("WORKSPACE") + File.separator + normalizedViewName);
                        }
                    };
                }

            };
        }
        else {
            return new BuildWrapper() {
                /**
                 * This method makes the build fail when a {@link ClearCaseUcmBaselineParameterDefinition}
                 * parameter is defined for the job, but the SCM is not an instance
                 * of {@link ClearCaseUcmBaselineSCM}.
                 */
                @Override
                public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                    String ccUcmBaselineSCMDisplayName = Hudson.getInstance().getDescriptor(ClearCaseUcmBaselineSCM.class).getDisplayName();
                    listener.fatalError(
                            "This job is not set up to use a '"
                            + ccUcmBaselineSCMDisplayName
                            + "' SCM while it has a '"
                            + Hudson.getInstance().getDescriptor(ClearCaseUcmBaselineParameterDefinition.class).getDisplayName()
                            + "' parameter: Either remove the parameter or set the SCM to be '"
                            + ccUcmBaselineSCMDisplayName
                            + "'; In the meantime: Aborting!");
                    return null;
                } 
            };
        }
    }

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        this.baseline = baseline;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getPromotionLevel() {
        return promotionLevel;
    }

    public void setPromotionLevel(String promotionLevel) {
        this.promotionLevel = promotionLevel;
    }

    public String getPvob() {
        return pvob;
    }

    public void setPvob(String pvob) {
        this.pvob = pvob;
    }

    public void setRestrictions(List<String> restrictions) {
        this.restrictions = restrictions;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public String getVob() {
        return vob;
    }

    public void setVob(String vob) {
        this.vob = vob;
    }

}
