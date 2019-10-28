/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail,
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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

import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.TagTuple;
import org.junit.*;
import picocli.CommandLine;

import java.util.*;

import static com.milaboratory.mixcr.cli.CommandAlign.parseTags;
import static org.junit.Assert.*;

public class CommandAlignTest {
    @Test
    public void parseTagsTest() {
        List<ParseTagsTestData> testData = Arrays.asList(
                new ParseTagsTestData(
                        Arrays.asList("G1", "G2"),
                        "M01:24:0000-A5Y06:1:11011 1:N:0:1~G1~GGTC~?/EA{49~53}|G2~CCAAA~AF100{53~58}",
                        new String[] { "GGTC", "CCAAA" }),
                new ParseTagsTestData(
                        Collections.singletonList("UMI"),
                        "UMI~GGAWNVGA~E?EGGE?/{57~65}",
                        new String[] { "GGAWNVGA" }),
                new ParseTagsTestData(
                        Arrays.asList("A", "B"),
                        "A~TTAG~????|B~C~D",
                        new String[] { "TTAG", "C" })
        );

        for (ParseTagsTestData currentTestData : testData) {
            TagTuple tagTuple = parseTags(new CommandLine(CommandAlign.class),
                    currentTestData.tags, currentTestData.read);
            assertArrayEquals(currentTestData.expectedTags, tagTuple.tags);
        }
    }

    private static class ParseTagsTestData {
        final List<String> tags;
        final SequenceRead read;
        final String[] expectedTags;

        ParseTagsTestData(List<String> tags, String description, String[] expectedTags) {
            this.tags = tags;
            this.read = new SingleReadImpl(0, NSequenceWithQuality.EMPTY, description);
            this.expectedTags = expectedTags;
        }
    }
}
