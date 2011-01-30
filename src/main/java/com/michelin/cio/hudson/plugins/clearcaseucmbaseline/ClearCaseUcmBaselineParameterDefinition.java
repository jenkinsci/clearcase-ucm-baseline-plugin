/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Française des Pneumatiques Michelin,
 * Romain Seguy, Amadeus SAS, Vincent Latombe
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
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.PluginImpl;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang.StringUtils;
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
 * <p>This parameter consists in a set of attributes to be set at config-time,
 * the most important being:<ul>
 * <li>The ClearCase UCM PVOB name;</li>
 * <li>The ClearCase UCM component name;</li>
 * <li>The ClearCase UCM promotion level (e.g. RELEASED);</li>
 * <li>The ClearCase UCM stream;</li>
 * <li>The ClearCase UCM view to create name.</li>
 * </ul></p>
 * <p>The following attribute is then asked at run-time:<ul>
 * <li>The ClearCase UCM baseline.</li>
 * </ul></p>
 *
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class ClearCaseUcmBaselineParameterDefinition extends ParameterDefinition implements Comparable<ClearCaseUcmBaselineParameterDefinition> {

    /**
     * Used to validate the {@link #moreRecentThan} field.
     */
    final static String MORE_RECENT_THAN_REGEX = "[0-9]+ *([Yy][Ee][Aa][Rr]|[Mm][Oo][Nn][Tt][Hh]|[Ww][Ee][Ee][Kk]|[Dd][Aa][Yy])[Ss]?";
    public final static String PARAMETER_NAME = "ClearCase UCM baseline";

    private final String component;
    /**
     * Allows excluding the "element * CHECKEDOUT" rule from the config spec (cf.
     * HUDSON-6411).
     */
    private final boolean excludeElementCheckedout;
    /**
     * dateFormat is used in getBaselines() to parse dates returned by cleartool.
     * No need to persist it --> transient.
     */
    private transient DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd.HHmmss");
    private final boolean forceRmview;
    private final String mkviewOptionalParam;
    /**
     * Allows displaying only baselines more recent than a given duration (cf.
     * HUDSON-8013).
     */
    private final String moreRecentThan;
    /**
     * The promotion level is optional: If not is set, then the user will be
     * offered with all the baselines of the ClearCase UCM component.
     */
    private final String promotionLevel;
    private final String pvob;
    /**
     * List of folders to be actually retrieved from CC. If no restriction is
     * defined, then the whole data will be downloaded. If some restrictions are
     * defined, then only the corresponding folders will be downloaded.
     */
    private final String restrictions;
    private final boolean snapshotView;
    /**
     * The stream is optional: If not set, the user will be proposed with all
     * baselines from the Clearcase UCM component.
     */
    private final String stream;
    private final boolean useUpdate;
    private final String viewName;
    /**
     * We use a UUID to uniquely identify each use of this parameter: We need this
     * to find the project and the node using this parameter in the getBaselines()
     * method (which is called before the build takes place).
     */
    private final UUID uuid;

    @DataBoundConstructor
    public ClearCaseUcmBaselineParameterDefinition(String pvob, String component, String promotionLevel, String stream, String restrictions, String viewName, String mkviewOptionalParam, boolean snapshotView, boolean useUpdate, boolean forceRmview, boolean excludeElementCheckedout, String moreRecentThan, String uuid) {
        super(PARAMETER_NAME); // we keep the name of the parameter not
                               // internationalized, it will save many
                               // issues when updating system settings
        // pvob should be prefixed with slave OS separator but at this stage, we
        // can't determine it.
        this.pvob = ClearCaseUcmBaselineUtils.prefixWithSeparator(pvob);
        this.component = component;
        this.promotionLevel = promotionLevel;
        this.stream = stream;
        this.restrictions = restrictions;
        this.viewName = viewName;
        this.mkviewOptionalParam = mkviewOptionalParam;
        this.snapshotView = snapshotView;
        this.useUpdate = useUpdate;
        this.forceRmview = forceRmview;
        this.excludeElementCheckedout = excludeElementCheckedout;
        this.moreRecentThan = moreRecentThan.trim();

        if(uuid == null || uuid.length() == 0) {
            this.uuid = UUID.randomUUID();
        }
        else {
            this.uuid = UUID.fromString(uuid);
        }
    }

    public int compareTo(ClearCaseUcmBaselineParameterDefinition pd) {
        if(pd.uuid.equals(uuid)) {
            return 0;
        }
        return -1;
    }

    /**
     * Computes, from the {@link #moreRecentThan} field, the date of the oldest
     * baseline to be displayed to the user.
     */
    private Date computeOldestBaselineDate() {
        String[] splittedMoreRecentThan = moreRecentThan.split("[ a-zA-Z]");
        splittedMoreRecentThan[1] = splittedMoreRecentThan[1].toLowerCase();

        int amount = Integer.parseInt(splittedMoreRecentThan[0]);
        if(splittedMoreRecentThan[1].startsWith("year")) {
            amount *= 365; // let's not worry about leap years
        }
        else if(splittedMoreRecentThan[1].startsWith("month")) {
            amount *= 31; // let's not worry about leap years
        }
        else if(splittedMoreRecentThan[1].startsWith("week")) {
            amount *= 7;
        }

        Calendar oldestBaselineDate = Calendar.getInstance();
        oldestBaselineDate.add(Calendar.DATE, -1 * amount);

        return oldestBaselineDate.getTime();
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
                    getName(), getPvob(), getComponent(), getPromotionLevel(),
                    getStream(), getViewName(), getMkviewOptionalParam(),
                    values[0], getUseUpdate(), getForceRmview(), getSnapshotView(),
                    getExcludeElementCheckedout());
        }
    }

    // This method is invoked when the user clicks on the "Build" button of Hudon's GUI
    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
        // bindJSON() uses the @DataBoundConstructor constructor
        ClearCaseUcmBaselineParameterValue value = req.bindJSON(ClearCaseUcmBaselineParameterValue.class, formData);

        value.setPvob(pvob);
        value.setComponent(component);
        value.setPromotionLevel(promotionLevel);
        value.setRestrictions(getRestrictionsAsList());
        value.setViewName(viewName);
        value.setMkviewOptionalParam(mkviewOptionalParam);
        // we don't set forceRmview: we use the value which is set by the user
        // (so it is in formData) to allow overriding the setting ==> the value
        // was set when invoking req.bindJSON()
        //value.setForceRmview(forceRmview);
        value.setSnapshotView(snapshotView);
        value.setUseUpdate(useUpdate);
        value.setExcludeElementCheckedout(excludeElementCheckedout);

        return value;
    }

    /**
     * Returns a list of ClearCase UCM baselines to be displayed in
     * {@code ClearCaseUcmBaselineParameterDefinition/index.jelly} (or {@code null}
     * if something wrong happens).
     */
    public List<String> getBaselines() throws IOException, InterruptedException {
        // cleartool lsbl -fmt "%[name]p " -level <promotion level> -stream <stream>@<pvob> -component <component>@<vob>
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(PluginImpl.getDescriptor().getCleartoolExe());
        cmd.add("lsbl");
        cmd.add("-fmt");
        cmd.add("%Nd %[name]p\n");
        if(StringUtils.isNotEmpty(promotionLevel)) {
            cmd.add("-level");
            cmd.add(promotionLevel);
        }
        if(StringUtils.isNotEmpty(stream)) {
            cmd.add("-stream");
            cmd.add(stream + '@' + pvob);
        }
        cmd.add("-component");
        cmd.add(component + '@' + pvob);

        // we have to find the node the job is assigned to so that we run the
        // cleartool command at the right place
        Node nodeRunningThisJob = Hudson.getInstance();
        for(Node node: Hudson.getInstance().getNodes()) {
            Computer computer = node.toComputer();
            if(computer != null) {
                List<AbstractProject> jobs = computer.getTiedJobs();
                if(jobs == null) {
                    continue;
                }

                boolean nodeRunningThisJobFound = false;
                for(AbstractProject project : jobs) {
                    ParametersDefinitionProperty property = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
                    if(property != null) {
                        ClearCaseUcmBaselineParameterDefinition pd = (ClearCaseUcmBaselineParameterDefinition) property.getParameterDefinition(PARAMETER_NAME);
                        if(pd != null) {
                            if(pd.compareTo(this) == 0) {
                                nodeRunningThisJob = node;
                                nodeRunningThisJobFound = true;
                                break;
                            }
                        }
                    }
                }

                if(nodeRunningThisJobFound) {
                    break;
                }
            }
        }

        // HUDSON-6057: we have to start the node if it's offline
        Computer computerRunnningThisJob = nodeRunningThisJob.toComputer();
        if(computerRunnningThisJob.isOffline()) {
            if(computerRunnningThisJob.isLaunchSupported()) {
                LOGGER.log(Level.INFO, "{0} is offline. Trying to launch it...", nodeRunningThisJob.getDisplayName());

                try {
                    computerRunnningThisJob.connect(false).get();

                    LOGGER.log(Level.INFO, "Waiting 10 seconds for {0} to be launched...", nodeRunningThisJob.getDisplayName());
                    Thread.sleep(10000);

                    do {
                        Thread.sleep(1000);
                    } while(computerRunnningThisJob.isConnecting());
                } catch(Exception e) {
                    LOGGER.log(Level.SEVERE, "An exception occurred while launching " + nodeRunningThisJob.getDisplayName(), e);
                    return null;
                }
            }
            else {
                LOGGER.log(Level.SEVERE, "{0} can't be automatically launched. You must start it before trying to run this job.", nodeRunningThisJob.getDisplayName());
                return null;
            }
        }

        if(computerRunnningThisJob.isOnline()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            nodeRunningThisJob.createLauncher(TaskListener.NULL).launch().cmds(cmd).stdout(baos).join();
            String cleartoolOutput = ClearCaseUcmBaselineUtils.processCleartoolOuput(baos);
            baos.close();

            if(cleartoolOutput.toString().contains("cleartool: Error")) {
                LOGGER.log(Level.WARNING, "An error occurred while gathering ClearCase UCM baselines: {0}", cleartoolOutput);
                return null;
            }
            else {
                List<String> baselines = Arrays.asList(cleartoolOutput.split("\n"));

                // baselines are sorted (to get the most recent date first)
                Collections.sort(baselines, new Comparator() {
                    public int compare(Object o1, Object o2) {
                        return -1 * ((String) o1).compareTo((String) o2);
                    }
                });

                // keep only baselines more recent than xxx
                if(StringUtils.isNotBlank(moreRecentThan) && moreRecentThan.matches(MORE_RECENT_THAN_REGEX)) {
                    final Date oldestBaselineDate = computeOldestBaselineDate();

                    CollectionUtils.filter(baselines, new Predicate() {
                        public boolean evaluate(Object object) {
                            try {
                                Date baselineDate = dateFormat.parse((String) object);
                                return baselineDate.after(oldestBaselineDate);
                            } catch(ParseException pe) {
                                // baselines with dates which can't be parsed are included
                                return true;
                            }
                        }
                    });
                }

                // the content of the list is tranformed to remove these unuseful dates
                baselines = (List<String>) CollectionUtils.collect(baselines, new Transformer() {
                    public Object transform(Object input) {
                        return ((String) input).substring(16);
                    }
                });

                return baselines;
            }
        }
        else {
            LOGGER.log(Level.SEVERE, "{0} is offline and couldn't be launched.", nodeRunningThisJob.getDisplayName());
            return null;
        }
    }

    public String getComponent() {
        return component;
    }

    public boolean getExcludeElementCheckedout() {
        return excludeElementCheckedout;
    }

    public boolean getForceRmview() {
        return forceRmview;
    }

    public String getMkviewOptionalParam() {
        return mkviewOptionalParam;
    }

    public String getMoreRecentThan() {
        return moreRecentThan;
    }

    public String getPromotionLevel() {
        return promotionLevel;
    }

    public String getPvob() {
        return pvob;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public List<String> getRestrictionsAsList() {
        ArrayList<String> restrictionsAsList = new ArrayList<String>();
        if(getRestrictions() != null && getRestrictions().length() > 0) {
            restrictionsAsList.addAll(Arrays.asList(Util.tokenize(getRestrictions(), "\n\r\f")));
        }
        return restrictionsAsList;
    }

    public boolean getSnapshotView() {
        return snapshotView;
    }

    public String getStream() {
        return stream;
    }

    public boolean getUseUpdate() {
        return useUpdate;
    }

    public String getViewName() {
        return viewName;
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        public FormValidation doCheckComponent(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("ComponentMustBeSet"));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckMoreRecentThan(@QueryParameter String value) {
            if(StringUtils.isNotBlank(value) && !value.matches(MORE_RECENT_THAN_REGEX)) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("InvalidMoreRecentThanFormat"));
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

        public FormValidation doCheckStream(@QueryParameter String value) {
            if(StringUtils.isEmpty(value)) {
                return FormValidation.warning(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("StreamShouldBeSet"));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckViewName(@QueryParameter String value) {
            if(value == null || value.length() == 0) {
                return FormValidation.error(ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("ViewNameMustBeSet"));
            }

            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return ResourceBundleHolder.get(ClearCaseUcmBaselineParameterDefinition.class).format("DisplayName");
        }

    }

    private final static Logger LOGGER = Logger.getLogger(ClearCaseUcmBaselineParameterDefinition.class.getName());

}
