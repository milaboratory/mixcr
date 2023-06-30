/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis.overlap;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.basictypes.ClnsReader;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.basictypes.VDJCSProperties;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.tests.IntegrationTest;
import com.milaboratory.util.Cache;
import com.milaboratory.util.LambdaSemaphore;
import gnu.trove.map.hash.TIntIntHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Category(IntegrationTest.class)
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
                .map(f -> new ClnsReader(
                        Paths.get(OverlapIntegrationTest.class.getResource("/sequences/big/yf_sample_data/" + f).getFile()),
                        VDJCLibraryRegistry.getDefault(),
                        concurrencyLimiter
                ))
                .collect(Collectors.toList());


        List<VDJCSProperties.VDJCSProperty<VDJCObject, ?>> byN = VDJCSProperties.orderingByNucleotide(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject, ?>> byAA = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject, ?>> byAAAndV = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3}, GeneType.Variable);

        long totalSumExpected = 0;
        for (List<VDJCSProperties.VDJCSProperty<VDJCObject, ?>> by : Arrays.asList(byN, byAA, byAAAndV)) {
            System.out.println("=============");
            Dataset<OverlapGroup<Clone>> overlap = OverlapUtil.INSTANCE.overlap(samples, by, readers);
            TIntIntHashMap hist = new TIntIntHashMap();
            long totalSum = 0;
            try (final OutputPort<OverlapGroup<Clone>> port = overlap.mkElementsPort()) {
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
}
