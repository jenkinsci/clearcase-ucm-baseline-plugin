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

import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.util.VariableResolver;

/**
 * This {@link VariableResolver} enhances the one defined by the ClearCase
 * plugin (cf. {@link hudson.plugins.clearcase.util.BuildVariableResolver}) to
 * add support for the {@code CLEARCASE_BASELINE} environment variable.
 *
 * <p>This class has been built to implement HUDSON-6410.</p>
 *
 * @author Romain Seguy  (http://openromain.blogspot.com)
 */
public class BuildVariableResolver implements VariableResolver<String> {

    private String baseline;
    private VariableResolver<String> superVariableResolver;

    public BuildVariableResolver(AbstractBuild<?, ?> build, final Launcher launcher, BuildListener listener, String baseline) {
        try {
            // this plugin is built against ClearCase plugin 1.0...
            superVariableResolver = new hudson.plugins.clearcase.util.BuildVariableResolver(build, launcher);
        }
        catch(NoSuchMethodError nsme) {
            // ...but it is also upward compatible with ClearCase plugin 1.1
            try {
                superVariableResolver = (VariableResolver) hudson.plugins.clearcase.util.BuildVariableResolver.class.getConstructors()[0].newInstance(build, Computer.currentComputer());
            } catch(Exception e) {
                listener.fatalError("No super variable resolver has been instantiated: The will surely lead to a crash...");
            }
        }

        this.baseline = baseline;
    }

    @Override
    public String resolve(String key) {
        if(ClearCaseUcmBaselineSCM.CLEARCASE_BASELINE_ENVSTR.equals(key)) {
            return baseline.trim();
        }

        return superVariableResolver.resolve(key);
    }

}
