/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail,
 * Popov Aleksandr (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import java.util.*;

public final class SerializerCompatibilityUtil {
    private SerializerCompatibilityUtil() {
    }

    private static final HashMap<String, String> classToNameMutable = new HashMap<>();
    private static final HashMap<String, String> nameToClassMutable = new HashMap<>();
    static final Map<String, String> classToName = Collections.unmodifiableMap(classToNameMutable);
    static final Map<String, String> nameToClass = Collections.unmodifiableMap(nameToClassMutable);

    static {
        classToNameMutable.put("\"com.milaboratory.cli.AppVersionInfo\"",
                "\"AppVersionInfo\"");
        classToNameMutable.put("\"com.milaboratory.cli.ActionConfiguration\"",
                "\"ActionConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.util.MiXCRVersionInfo\"",
                "\"MiXCRVersionInfo\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandAlign$AlignConfiguration\"",
                "\"CommandAlign$AlignConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandAssemble$AssembleConfiguration\"",
                "\"CommandAssemble$AssembleConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandAssembleContigs$AssembleContigsConfiguration\"",
                "\"CommandAssembleContigs$AssembleContigsConfiguration\"");
        classToNameMutable.put(
                "\"com.milaboratory.mixcr.cli.CommandAssemblePartialAlignments$AssemblePartialConfiguration\"",
                "\"CommandAssemblePartialAlignments$AssemblePartialConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandExtend$ExtendConfiguration\"",
                "\"CommandExtend$ExtendConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandFilterAlignments$FilterConfiguration\"",
                "\"CommandFilterAlignments$FilterConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandMergeAlignments$MergeConfiguration\"",
                "\"CommandMergeAlignments$MergeConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandSlice$SliceConfiguration\"",
                "\"CommandSlice$SliceConfiguration\"");
        classToNameMutable.put("\"com.milaboratory.mixcr.cli.CommandSortAlignments$SortConfiguration\"",
                "\"CommandSortAlignments$SortConfiguration\"");

        for (HashMap.Entry<String, String> entry : classToNameMutable.entrySet())
            nameToClassMutable.put(entry.getValue(), entry.getKey());
    }
}
