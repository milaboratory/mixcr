/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.assembler.fullseq;

import com.google.common.util.concurrent.AtomicDouble;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler.VariantBranch;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.cli.AbstractCommandReportBuilder;
import io.repseq.core.GeneFeature;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 *
 */
public class FullSeqAssemblerReportBuilder extends AbstractCommandReportBuilder {
    private final AtomicInteger assemblePrematureTerminationCount = new AtomicInteger(0);
    private final AtomicInteger initialCloneCount = new AtomicInteger(0);
    private final AtomicInteger finalCloneCount = new AtomicInteger(0);
    private final AtomicDouble totalReadsProcessed = new AtomicDouble(0);
    private final AtomicInteger clonesClustered = new AtomicInteger(0);
    private final AtomicDouble readsClustered = new AtomicDouble(0);
    private final AtomicDouble totalDividedVariantReads = new AtomicDouble(0);
    private final AtomicInteger longestContigLength = new AtomicInteger(0);
    private final AtomicLong variantsBeforeClustering = new AtomicLong(0);
    private final AtomicInteger canceledAssemblies = new AtomicInteger(0);
    private final AtomicLong vHitsReorder = new AtomicLong(0);
    private final AtomicLong jHitsReorder = new AtomicLong(0);

    private final AtomicLong clonesWithAmbiguousLetters = new AtomicLong(0);
    private final AtomicLong clonesWithAmbiguousLettersInSplittingRegion = new AtomicLong(0);
    private final AtomicDouble readsWithAmbiguousLetters = new AtomicDouble(0);
    private final AtomicDouble readsWithAmbiguousLettersInSplittingRegion = new AtomicDouble(0);
    private final AtomicLong totalAmbiguousLettersInSplittingRegion = new AtomicLong(0);
    private final AtomicLong totalAmbiguousLetters = new AtomicLong(0);

    public int getInitialCloneCount() {
        return initialCloneCount.get();
    }

    public int getFinalCloneCount() {
        return finalCloneCount.get();
    }
    public void onVariantClustered(VariantBranch minor) {
        clonesClustered.incrementAndGet();
        readsClustered.addAndGet(minor.count);
    }

    public void onEmptyOutput(Clone clone) {
        assemblePrematureTerminationCount.incrementAndGet();
    }

    public void onVariantsCreated(List<VariantBranch> branches) {
        variantsBeforeClustering.addAndGet(branches.size());
    }

    public void onVHitReorder() {
        vHitsReorder.incrementAndGet();
    }

    public void onJHitReorder() {
        jHitsReorder.incrementAndGet();
    }

    public void onAssemblyCanceled(Clone initialClone) {
        canceledAssemblies.incrementAndGet();
        initialCloneCount.incrementAndGet();
        finalCloneCount.incrementAndGet();
        totalReadsProcessed.addAndGet(initialClone.getCount());
    }

    public void afterVariantsClustered(Clone initialClone, Clone[] branches, GeneFeature subCloningRegion) {
        initialCloneCount.incrementAndGet();
        finalCloneCount.addAndGet(branches.length);
        totalReadsProcessed.addAndGet(initialClone.getCount());
        if (branches.length > 1)
            totalDividedVariantReads.addAndGet(Arrays.stream(branches, 1, branches.length).mapToDouble(Clone::getCount).sum());
        int maxLength = Arrays.stream(branches)
                .mapToInt(clone -> IntStream.range(0, clone.numberOfTargets()).map(i -> clone.getTarget(i).size()).sum())
                .max().orElse(0);
        longestContigLength.accumulateAndGet(maxLength, Math::max);

        for (Clone branch : branches) {
            int numberOfN = Arrays.stream(branch.getTargets()).mapToInt(t -> numberOfWildcards(t.getSequence())).sum();
            if (numberOfN > 0) {
                clonesWithAmbiguousLetters.incrementAndGet();
                readsWithAmbiguousLetters.addAndGet(branch.getCount());
                totalAmbiguousLetters.addAndGet(numberOfN);
            }

            if (subCloningRegion != null) {
                VDJCObject.CaseSensitiveNucleotideSequence f = branch.getIncompleteFeature(subCloningRegion);
                if (f != null) {
                    int numberOfNInSplittingRegion = numberOfWildcards(f);
                    if (numberOfNInSplittingRegion > 0) {
                        clonesWithAmbiguousLettersInSplittingRegion.incrementAndGet();
                        readsWithAmbiguousLettersInSplittingRegion.addAndGet(branch.getCount());
                        totalAmbiguousLettersInSplittingRegion.addAndGet(numberOfNInSplittingRegion);
                    }
                }
            }
        }
    }

    private static int numberOfWildcards(NucleotideSequence seq) {
        int count = 0;
        for (int i = 0; i < seq.size(); i++)
            if (NucleotideSequence.ALPHABET.isWildcard(seq.codeAt(i)))
                count++;
        return count;
    }

    private static int numberOfWildcards(VDJCObject.CaseSensitiveNucleotideSequence csSeq) {
        if (csSeq == null)
            return 0;
        int count = 0;
        for (int i = 0; i < csSeq.size(); i++)
            count += numberOfWildcards(csSeq.getSequence(i));
        return count;
    }

    @Override
    public FullSeqAssemblerReport buildReport() {
        return new FullSeqAssemblerReport(
                getDate(),
                getCommandLine(),
                getInputFiles(),
                getOutputFiles(),
                getExecutionTimeMillis(),
                getVersion(),
                clonesWithAmbiguousLetters.get(),
                clonesWithAmbiguousLettersInSplittingRegion.get(),
                readsWithAmbiguousLetters.get(),
                readsWithAmbiguousLettersInSplittingRegion.get(),
                totalAmbiguousLetters.get(),
                totalAmbiguousLettersInSplittingRegion.get(),
                initialCloneCount.get(),
                canceledAssemblies.get(),
                finalCloneCount.get(),
                totalReadsProcessed.get(),
                clonesClustered.get(),
                readsClustered.get(),
                longestContigLength.get(),
                totalDividedVariantReads.get(),
                assemblePrematureTerminationCount.get());
    }
}
