/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Romain Seguy,
 *                     Amadeus SAS, Vincent Latombe
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
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.AbstractClearCaseScm;
import hudson.plugins.clearcase.ClearToolLauncher;
import hudson.plugins.clearcase.HudsonClearToolLauncher;
import hudson.plugins.clearcase.PluginImpl;
import hudson.plugins.clearcase.ucm.UcmMakeBaseline;
import hudson.plugins.clearcase.ucm.UcmMakeBaselineComposite;
import hudson.plugins.clearcase.util.PathUtil;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.VariableResolver;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * This class represents the actual {@link ParameterValue} for the
 * {@link ClearCaseUcmBaselineParameterDefinition} parameter.
 *
 * <p>This value holds the following information:
 * <li>The ClearCase UCM PVOB name (which is actually defined at config-time &mdash;
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
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class ClearCaseUcmBaselineParameterValue extends ParameterValue {

    // TODO move the attributes of this class and of ClearCaseUcmBaselineParameterDefinition
    // to a single dedicated class to avoid having too much duplicated code
    @Exported(visibility=3) private String baseline;        // this att is set by the user once the build takes place
    @Exported(visibility=3) private String component;       // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private boolean excludeElementCheckedout; // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private boolean forceRmview;    // this att can be overriden by the user but default value
                                                            // comes from ClearCaseUcmBaselineParameterDefinition
    private String mkviewOptionalParam;                     // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private String promotionLevel;  // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private String pvob;            // this att comes from ClearCaseUcmBaselineParameterDefinition
    private List<String> restrictions;                      // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private boolean snapshotView;   // this att comes from ClearCaseUcmBaselineParameterDefinition
    private String stream;                                  // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private boolean useUpdate;      // this att comes from ClearCaseUcmBaselineParameterDefinition
    @Exported(visibility=3) private String viewName;        // this att comes from ClearCaseUcmBaselineParameterDefinition

    private StringBuffer fatalErrorMessage = new StringBuffer();

    // I have to have two constructors: If I use only one (the most complete one),
    // I get an exception in ClearCaseUcmBaselineParameterDefinition.createValue(StaplerRequest, JSONObject)
    // while invoking req.bindJSON()
    // Is it because of the two booleans? No time to investigate, sorry.
    @DataBoundConstructor
    public ClearCaseUcmBaselineParameterValue(String name, String baseline, boolean forceRmview) {
        this(name, null, null, null, null, null, null, baseline, false, forceRmview, false, false);
    }

    public ClearCaseUcmBaselineParameterValue(
            String name, String pvob, String component, String promotionLevel,
            String stream, String viewName, String mkviewOptionalParam, String baseline,
            boolean useUpdate, boolean forceRmview, boolean snapshotView,
            boolean excludeElementCheckedout) {
        super(name);
        this.pvob = ClearCaseUcmBaselineUtils.prefixWithSeparator(pvob);
        this.component = component;
        this.promotionLevel = promotionLevel;
        this.stream = stream;
        this.viewName = viewName;
        this.mkviewOptionalParam = mkviewOptionalParam;
        this.baseline = baseline;
        this.useUpdate = useUpdate;
        this.forceRmview = forceRmview;
        this.snapshotView = snapshotView;
        if(this.snapshotView) {
            // the "element * CHECKEDOUT" rule is mandatory for snapshot views
            this.excludeElementCheckedout = true;
        }
        else {
            this.excludeElementCheckedout = excludeElementCheckedout;
        }
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
            fatalErrorMessage.append("The value '").append(baseline).append("' is not a valid ClearCase UCM baseline.");
        }

        // HUDSON-5877: let's ensure the job has no publishers/notifiers coming
        // from the ClearCase plugin
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = build.getProject().getPublishersList();
        for(Publisher publisher : publishersList) {
            if(publisher instanceof UcmMakeBaseline || publisher instanceof UcmMakeBaselineComposite) {
                if(fatalErrorMessage.length() > 0) {
                    fatalErrorMessage.append('\n');
                }
                fatalErrorMessage.append("This job is set up to use a '").append(publisher.getDescriptor().getDisplayName()).append(
                        "' publisher which is not compatible with the ClearCase UCM baseline SCM mode. Please remove this publisher.");
            }
        }
        if(fatalErrorMessage.length() > 0) {
            return new BuildWrapper() {
                /**
                 * This method just makes the build fail for various reasons.
                 */
                @Override
                public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                    listener.fatalError(fatalErrorMessage.toString());
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
                public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                    // we use our own variable resolver to have the support for
                    // the CLEARCASE_BASELINE env variable (cf. HUDSON-6410)
                    VariableResolver variableResolver = new BuildVariableResolver(build, launcher, listener, baseline);

                    ClearToolLauncher clearToolLauncher = createClearToolLauncher(listener, build.getProject().getSomeWorkspace(), launcher);
                    ClearToolUcmBaseline cleartool = new ClearToolUcmBaseline(variableResolver, clearToolLauncher);

                    viewName = generateNormalizedViewName(variableResolver, viewName);

                    FilePath workspace = build.getProject().getSomeWorkspace();
                    FilePath viewPath = workspace.child(viewName);
                    StringBuilder configSpec = new StringBuilder();

                    // --- 0. Has the same baseline been retrieved during last execution? ---

                    boolean lastBuildUsedSameBaseline = false;

                    if(forceRmview == false) { // we care only if we don't want to remove the existing view
                        ClearCaseUcmBaselineParameterValue lastCcParamValue = null;

                        // tons of loops and ifs to find the ClearCaseUcmBaselineParameterValue of the last build, if any
                        List<Run> builds = build.getProject().getBuilds();
                        if(builds.size() > 1) {
                            Run latestBuild = builds.get(1); // builds.get(0) is the currently running build
                            List<ParametersAction> actions = latestBuild.getActions(ParametersAction.class);
                            if(actions != null) {
                                for(ParametersAction action : actions) {
                                    List<ParameterValue> parameters = action.getParameters();
                                    if(parameters != null) {
                                        for(ParameterValue parameter : parameters) {
                                            if(parameter instanceof ClearCaseUcmBaselineParameterValue) {
                                                lastCcParamValue = (ClearCaseUcmBaselineParameterValue) parameter;
                                                // there can be only one time this kind of parameter, so let's break
                                                break;
                                            }
                                        }
                                    }

                                    if(lastCcParamValue != null) {
                                        // there can be only one time this kind of parameter, so let's break here too
                                        break;
                                    }
                                }
                            }
                        }

                        if(lastCcParamValue != null) {
                            if(pvob.equals(lastCcParamValue.pvob)
                                    && component.equals(lastCcParamValue.component)
                                    && baseline.equals(lastCcParamValue.baseline)) {
                                // the baseline used in the latest build is the same as the newly requested one
                                lastBuildUsedSameBaseline = true;
                            }
                        }
                    }

                    final String newlineForOS = launcher.isUnix() ? "\n" : "\r\n";
                    // we assume that the slave OS file separator is the same as the server
                    // since we have no way of determining the OS of the Clearcase server
                    final String fileSepForOS = PathUtil.fileSepForOS(launcher.isUnix());

                    if(forceRmview || !lastBuildUsedSameBaseline || !viewPath.exists()) {
                        // --- 1. We remove the view if it already exists ---

                        if(viewPath.exists()) {
                            if(!useUpdate || forceRmview) {
                                cleartool.rmview(viewName);

                                // --- 2. We create the view to be loaded ---

                                cleartool.mkview(viewName, mkviewOptionalParam, snapshotView, null);
                            }
                        } else {
                            cleartool.mkview(viewName, mkviewOptionalParam, snapshotView, null);
                        }

                        // --- 3. We create the configspec ---

                        if(!excludeElementCheckedout) {
                            configSpec.append("element * CHECKEDOUT").append(newlineForOS);
                        }

                        Set<String> loadRules = new HashSet<String>(); // we use a Set to avoid duplicate load rules (cf. HUDSON-6398)

                        // cleartool lsbl -fmt "%[depends_on_closure]p" <baseline>@<pvob>
                        String[] dependentBaselines = cleartool.getDependentBaselines(pvob, baseline);

                        // we add the selected baseline at the beginning of the dependentBaselines
                        // array so that the "element" and "load" sections of the config spec are
                        // generated for this baseline
                        dependentBaselines = (String[]) ArrayUtils.add(dependentBaselines, 0, baseline + '@' + pvob);

                        for(String dependentBaselineSelector: dependentBaselines) {
                            int indexOfSeparator = dependentBaselineSelector.indexOf('@');
                            if(indexOfSeparator == -1) {
                                if(LOGGER.isLoggable(Level.INFO)) {
                                    LOGGER.log(Level.INFO, "Ignoring dependent baseline '{0}{1}", new Object[]{dependentBaselineSelector, '\''});
                                }
                                continue;
                            }

                            String dependentBaseline = dependentBaselineSelector.substring(0, indexOfSeparator);
                            String component = cleartool.getComponentFromBaseline(pvob, dependentBaseline);
                            String componentRootDir = cleartool.getComponentRootDir(pvob, component);

                            // some components may be rootless: they must simply be skipped (cf. HUDSON-6398)
                            if(StringUtils.isBlank(componentRootDir)) {
                                continue;
                            }

                            // example of generated config spec "element":
                            // element /xxx/spd_comp/... spd_comp_v1.x_20100402100000 -nocheckout
                            configSpec.append("element \"").append(componentRootDir).append(fileSepForOS).append("...\" ").append(dependentBaseline).append(" -nocheckout").append(newlineForOS);
                            // is any download restriction defined?
                            if(restrictions != null && restrictions.size() > 0) {
                                for(String restriction: restrictions) {
                                    // the comparison must not take into account path separators,
                                    // so let's unify them to / for that purpose
                                    String restrictionForComparison = restriction.replace('\\', '/');
                                    String componentRootDirForComparison = componentRootDir.replace('\\', '/');
                                    if(restrictionForComparison.startsWith(componentRootDirForComparison)) {
                                        // example of generated config spec "load":
                                        // load /xxx/spd_comp/src
                                        loadRules.add("load " + restriction);
                                    }
                                }
                            }
                            else {
                                loadRules.add("load " + componentRootDir);
                            }
                        }

                        configSpec.append("element * /main/0 -ucm -nocheckout").append(newlineForOS);
                        for(String loadRule: loadRules) {
                            configSpec.append(loadRule).append(newlineForOS);
                        }

                        listener.getLogger().println("The view will be created based on the following config spec:");
                        listener.getLogger().println("--- config spec start ---");
                        listener.getLogger().print(configSpec.toString());
                        listener.getLogger().println("---  config spec end  ---");

                        // --- 4. We actually load the view based on the configspec ---

                        // cleartool setcs <configspec>
                        cleartool.setcs(viewName, configSpec.toString());
                    }
                    else {
                        listener.getLogger().println("The requested ClearCase UCM baseline is the same as previous build: Reusing previously loaded view");
                    }

                    // --- 5. Create the environment variables ---

                    return new Environment() {
                        @Override
                        public void buildEnvVars(Map<String, String> env) {
                            env.put(ClearCaseUcmBaselineSCM.CLEARCASE_BASELINE_ENVSTR,
                                    baseline);
                            env.put(AbstractClearCaseScm.CLEARCASE_VIEWNAME_ENVSTR,
                                    viewName);
                            env.put(AbstractClearCaseScm.CLEARCASE_VIEWPATH_ENVSTR,
                                    env.get("WORKSPACE") + fileSepForOS + viewName);
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

    public boolean getExcludeElementCheckedout() {
        return excludeElementCheckedout;
    }

    public void setExcludeElementCheckedout(boolean excludeElementCheckedout) {
        this.excludeElementCheckedout = excludeElementCheckedout;
    }

    public boolean getForceRmview() {
        return forceRmview;
    }

    public void setForceRmview(boolean forceRmview) {
        this.forceRmview = forceRmview;
    }

    public String getMkviewOptionalParam() {
        return mkviewOptionalParam;
    }

    public void setMkviewOptionalParam(String mkviewOptionalParam) {
        this.mkviewOptionalParam = mkviewOptionalParam;
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

    public boolean getSnapshotView() {
        return snapshotView;
    }

    public void setSnapshotView(boolean snapshotView) {
        this.snapshotView = snapshotView;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public boolean getUseUpdate() {
        return useUpdate;
    }

    public void setUseUpdate(boolean useUpdate) {
        this.useUpdate = useUpdate;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    private final static Logger LOGGER = Logger.getLogger(ClearCaseUcmBaselineParameterValue.class.getName());

}
