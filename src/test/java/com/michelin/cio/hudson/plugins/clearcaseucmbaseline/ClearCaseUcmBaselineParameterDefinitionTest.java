/*
 * The MIT License
 *
 * Copyright (c) 2011, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import junit.framework.TestCase;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;

public class ClearCaseUcmBaselineParameterDefinitionTest extends TestCase {

    /**
     * This method is more about building an algorithm than testing the method
     * which uses it.
     */
    public void testGetBaselines() {
        List<String> expectedBaselines = new ArrayList<String>() {{
            add("xyz_v2.4.0_20051205");
            add("xyz_v2.3.1_20050531");
            add("xyz_v2.3.0_20050309");
            add("xyz_v2.2.3_20050503");
            add("xyz_v2.2.2_20041025");
            add("xyz_v2.2.1_20040806");
            add("xyz_v2.2.0_20040503");
            add("xyz_v2.1.2_20040607");
            add("xyz_v2.1.1_20040211");
            add("xyz_v2.1.0_20031016");
            add("xyz_v2.0.2_20040113");
            add("xyz_v2.0.1_20030929");
            add("xyz_v2.0.0_20030626");
            add("xyz_v2.x_init");
            add("xyz_INITIAL");
        }};

        String cleartoolOutput = "20051124.145458 xyz_INITIAL\n"
                + "20051124.163732 xyz_v2.x_init\n"
                + "20051128.085745 xyz_v2.0.0_20030626\n"
                + "20051128.135749 xyz_v2.0.1_20030929\n"
                + "20051128.140834 xyz_v2.0.2_20040113\n"
                + "20051128.175618 xyz_v2.1.1_20040211\n"
                + "20051128.160803 xyz_v2.1.0_20031016\n"
                + "20051129.094900 xyz_v2.1.2_20040607\n"
                + "20051129.115212 xyz_v2.2.0_20040503\n"
                + "20051129.141546 xyz_v2.2.1_20040806\n"
                + "20051129.143813 xyz_v2.2.2_20041025\n"
                + "20051129.145120 xyz_v2.2.3_20050503\n"
                + "20051130.110900 xyz_v2.3.1_20050531\n"
                + "20051130.091037 xyz_v2.3.0_20050309\n"
                + "20060228.152608 xyz_v2.4.0_20051205\n";

        List<String> baselines = Arrays.asList(cleartoolOutput.split("\n"));
        Collections.sort(baselines, new Comparator() {
            // reverse comparator so that we get the latest baselines first
            public int compare(Object o1, Object o2) {
                return -1 * ((String) o1).compareTo((String) o2);
            }
        });

        baselines = (List<String>) CollectionUtils.collect(baselines, new Transformer() {
            public Object transform(Object input) {
                return ((String) input).substring(16);
            }
        });

        if(expectedBaselines.size() != baselines.size()) {
            fail();
        }

        for(int i = expectedBaselines.size()-1; i >= 0; i--) {
            if(!expectedBaselines.get(i).equals(baselines.get(i))) {
                fail();
            }
        }
    }

    /**
     * This method is more about building an algorithm than testing the method
     * which uses it.
     */
    public void testMoreRecentThanRegex() {
        assertTrue("1 year".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("2 years".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("3years".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("10 years".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));

        assertTrue("1 month".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("2 months".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("3months".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("10 months".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));

        assertTrue("1 week".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("2 weeks".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("3weeks".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("10 weeks".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));

        assertTrue("1 day".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("2 days".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("3days".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertTrue("10 days".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));

        assertFalse("year".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertFalse("month".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertFalse("week".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
        assertFalse("day".matches(ClearCaseUcmBaselineParameterDefinition.MORE_RECENT_THAN_REGEX));
    }

}
