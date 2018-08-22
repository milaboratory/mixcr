/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using the following email
 * addresses: licensing@milaboratory.com
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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.util.RandomUtil;
import com.milaboratory.util.TempFileManager;
import gnu.trove.set.hash.TLongHashSet;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class AlignmentsToClonesMappingContainerTest {
    @Test
    public void test1() throws Exception {
        test(20000, 200000, 10, 20000, 17);
        test(200, 2000, 10, 2000, 2);
        test(20000, 2000000, 10, 20000, 1048576);
    }

    public void test(int minRecords, int maxRecords, int minClones, int maxClones, int sortChunk) throws Exception {
        Well19937c rnd = RandomUtil.getThreadLocalRandom();
        RandomDataGenerator rndD = RandomUtil.getThreadLocalRandomData();

        int readsCount, clonesCount;

        do {
            readsCount = minRecords + rnd.nextInt(maxRecords - minRecords);
            clonesCount = minClones + rnd.nextInt(maxClones - minClones);
        } while (readsCount <= clonesCount);

        ReadToCloneMapping[] mappings = new ReadToCloneMapping[readsCount];
        TLongHashSet[] clones = new TLongHashSet[clonesCount];

        int[] initialReads = rndD.nextPermutation(mappings.length, clones.length);

        Assert.assertEquals(initialReads.length, clones.length);

        for (int i = 0; i < clones.length; i++) {
            mappings[initialReads[i]] = new ReadToCloneMapping(initialReads[i], initialReads[i], i, false, false, false, false);
            clones[i] = new TLongHashSet();
            clones[i].add(initialReads[i]);
        }

        int goodMappings = clones.length;

        for (int i = 0; i < mappings.length; i++) {
            if (mappings[i] != null)
                continue;
            if (rnd.nextInt(100) > 80) // 20% dropped
                mappings[i] = new ReadToCloneMapping(i, i, -1, false, false,
                        false, false);
            else {
                ++goodMappings;
                int cloneId = rnd.nextInt(clones.length);
                mappings[i] = new ReadToCloneMapping(i, i, cloneId, rnd.nextBoolean(), rnd.nextBoolean(),
                        false, rnd.nextBoolean());
                clones[cloneId].add(i);
            }
        }

        File tempFile = TempFileManager.getTempFile();
        try(DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            AlignmentsToClonesMappingContainer.writeMapping(CUtils.asOutputPort(mappings), clones.length, dos, sortChunk);
        }

        //System.out.println(tempFile.length());

        AlignmentsToClonesMappingContainer container = AlignmentsToClonesMappingContainer.open(tempFile);

        Assert.assertEquals(clones.length, container.getCloneCount());
        Assert.assertEquals(goodMappings, container.getAlignmentCount());

        OutputPort<ReadToCloneMapping> portByAlignments = container.createPortByAlignments();
        for (int i = 0; i < mappings.length; i++) {
            if (mappings[i].isDropped())
                continue;
            Assert.assertEquals(mappings[i], portByAlignments.take());
        }

        int[] sClonesId = rndD.nextPermutation(clones.length, clones.length);

        for (int j = 0; j < clones.length; j++) {
            int i = sClonesId[j];
            for (ReadToCloneMapping reads : CUtils.it(container.createPortForClone(i)))
                Assert.assertTrue(clones[i].remove(reads.alignmentsId));
            Assert.assertTrue(clones[i].isEmpty());
        }
    }
}