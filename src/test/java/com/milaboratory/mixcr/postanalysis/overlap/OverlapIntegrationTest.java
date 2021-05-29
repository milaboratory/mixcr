/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.postanalysis.overlap;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.ClnsReader;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.basictypes.VDJCSProperties;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.util.Cache;
import com.milaboratory.util.LambdaSemaphore;
import gnu.trove.map.hash.TIntIntHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OverlapIntegrationTest {
    @Test
    public void test1() throws IOException {
        // Limits concurrency across all readers
        LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(4);

        List<String> samples = Arrays.asList("Ig-2_S2.contigs.clns",
                "Ig-3_S3.contigs.clns",
                "Ig-4_S4.contigs.clns",
                "Ig-5_S5.contigs.clns",
                "Ig1_S1.contigs.clns",
                "Ig2_S2.contigs.clns",
                "Ig3_S3.contigs.clns",
                "Ig4_S4.contigs.clns",
                "Ig5_S5.contigs.clns");
        List<ClnsReader> readers = samples.stream()
                .map(f -> {
                    try {
                        return new ClnsReader(
                                Paths.get(OverlapIntegrationTest.class.getResource("/sequences/big/yf_sample_data/" + f).getFile()),
                                VDJCLibraryRegistry.getDefault(),
                                concurrencyLimiter);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());


        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byN = VDJCSProperties.orderingByNucleotide(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byAA = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byAAAndV = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3}, GeneType.Variable);

        long totalSumExpected = 0;
        for (List<VDJCSProperties.VDJCSProperty<VDJCObject>> by : Arrays.asList(byN, byAA, byAAAndV)) {
            System.out.println("=============");
            Dataset<OverlapGroup<Clone>> overlap = OverlapUtil.overlap(samples, by, readers);
            TIntIntHashMap hist = new TIntIntHashMap();
            long totalSum = 0;
            try (final OutputPortCloseable<OverlapGroup<Clone>> port = overlap.mkElementsPort()) {
                for (OverlapGroup<Clone> cloneOverlapGroup : CUtils.it(port)) {
                    int sum = cloneOverlapGroup.elements.stream().mapToInt(l -> l.isEmpty() ? 0 : 1).sum();
                    totalSum += cloneOverlapGroup.elements.stream().mapToInt(List::size).sum();
                    hist.adjustOrPutValue(sum, 1, 1);
                }
            }
            if (totalSumExpected == 0)
                totalSumExpected = totalSum;
            else
                Assert.assertEquals(totalSumExpected, totalSum);
            for (int i = 1; i < readers.size(); i++)
                if (hist.get(i) != 0)
                    System.out.println("" + i + ": " + hist.get(i));
        }

        System.out.println("Cache misses: " + Cache.totalCacheMisses());
        System.out.println("  Cache hits: " + Cache.totalCacheHits());
    }

    @Ignore
    @Test
    public void tst1() throws IOException {
        LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(4);

        List<Path> input = Files.list(Paths.get("/Users/dbolotin/Downloads/RealAnalysisRita/workdir/"))
                .map(p -> p.resolve("mixcr"))
                .filter(Files::exists)
                .flatMap(p -> {
                    try {
                        return Files.list(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(p -> p.toString().endsWith(".clns"))
                .collect(Collectors.toList());

        List<ClnsReader> readers = input.stream()
                .map(f -> {
                    try {
                        return new ClnsReader(f,
                                VDJCLibraryRegistry.getDefault(),
                                concurrencyLimiter);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byN = VDJCSProperties.orderingByNucleotide(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byAA = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byAAAndV = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3}, GeneType.Variable);

        for (List<VDJCSProperties.VDJCSProperty<VDJCObject>> by : Arrays.asList(byN, byAA, byAAAndV)) {
            System.out.println("=============");
            Dataset<OverlapGroup<Clone>> overlap = OverlapUtil.overlap(input.stream().map(Path::toString).collect(Collectors.toList()), by, readers);
            TIntIntHashMap hist = new TIntIntHashMap();
            long totalSum = 0;
            try (OutputPortCloseable<OverlapGroup<Clone>> port = overlap.mkElementsPort()) {
                for (OverlapGroup<Clone> cloneOverlapGroup : CUtils.it(port)) {
                    int sum = cloneOverlapGroup.elements.stream().mapToInt(l -> l.isEmpty() ? 0 : 1).sum();
                    totalSum += cloneOverlapGroup.elements.stream().mapToInt(List::size).sum();
                    hist.adjustOrPutValue(sum, 1, 1);
                }
            }
            for (int i = 1; i < readers.size(); i++)
                if (hist.get(i) != 0)
                    System.out.println("" + i + ": " + hist.get(i));
            System.out.println("Total: " + totalSum);
        }

        System.out.println("Cache misses: " + Cache.totalCacheMisses());
        System.out.println("  Cache hits: " + Cache.totalCacheHits());
    }
}
