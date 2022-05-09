/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.assembler.fullseq;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.AtomicDouble;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler.VariantBranch;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.cli.Report;
import com.milaboratory.util.ReportHelper;
import io.repseq.core.GeneFeature;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 *
 */
public class FullSeqAssemblerReport implements Report {
    private final AtomicInteger assemblePrematureTerminationCount = new AtomicInteger(0);
    private final AtomicInteger initialCloneCount = new AtomicInteger(0);
    private final AtomicInteger finalCloneCount = new AtomicInteger(0);
    private final AtomicDouble totalReads = new AtomicDouble(0);
    private final AtomicInteger totalClustered = new AtomicInteger(0);
    private final AtomicDouble totalClusteredReads = new AtomicDouble(0);
    private final AtomicDouble dividedReads = new AtomicDouble(0);
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

    public void onVariantClustered(VariantBranch minor) {
        totalClustered.incrementAndGet();
        totalClusteredReads.addAndGet(minor.count);
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
        totalReads.addAndGet(initialClone.getCount());
    }

    public void afterVariantsClustered(Clone initialClone, Clone[] branches, GeneFeature subCloningRegion) {
        initialCloneCount.incrementAndGet();
        finalCloneCount.addAndGet(branches.length);
        totalReads.addAndGet(initialClone.getCount());
        if (branches.length > 1)
            dividedReads.addAndGet(Arrays.stream(branches, 1, branches.length).mapToDouble(Clone::getCount).sum());
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
        if(csSeq == null)
            return 0;
        int count = 0;
        for (int i = 0; i < csSeq.size(); i++)
            count += numberOfWildcards(csSeq.getSequence(i));
        return count;
    }

    @JsonProperty("clonesWithAmbiguousLetters")
    public long getClonesWithAmbiguousLetters() {
        return clonesWithAmbiguousLetters.get();
    }

    @JsonProperty("clonesWithAmbiguousLettersInSplittingRegion")
    public long getClonesWithAmbiguousLettersInSplittingRegion() {
        return clonesWithAmbiguousLettersInSplittingRegion.get();
    }

    @JsonProperty("readsWithAmbiguousLetters")
    public double getReadsWithAmbiguousLetters() {
        return readsWithAmbiguousLetters.get();
    }

    @JsonProperty("readsWithAmbiguousLettersInSplittingRegion")
    public double getReadsWithAmbiguousLettersInSplittingRegion() {
        return readsWithAmbiguousLettersInSplittingRegion.get();
    }

    @JsonProperty("totalAmbiguousLetters")
    public long getTotalAmbiguousLetters() {
        return totalAmbiguousLetters.get();
    }

    @JsonProperty("totalAmbiguousLettersInSplittingRegion")
    public long getTotalAmbiguousLettersInSplittingRegion() {
        return totalAmbiguousLettersInSplittingRegion.get();
    }

    @JsonProperty("initialCloneCount")
    public int getInitialCloneCount() {
        return initialCloneCount.get();
    }

    @JsonProperty("canceledAssemblies")
    public int getCanceledAssemblies() {
        return canceledAssemblies.get();
    }

    @JsonProperty("finalCloneCount")
    public int getFinalCloneCount() {
        return finalCloneCount.get();
    }

    @JsonProperty("totalReadsProcessed")
    public double getTotalReads() {
        return totalReads.get();
    }

    @JsonProperty("clonesClustered")
    public int getClonesClustered() {
        return totalClustered.get();
    }

    @JsonProperty("readsClustered")
    public double getReadsClustered() {
        return totalClusteredReads.get();
    }

    @JsonProperty("longestContigLength")
    public int getLongestContigLength() {
        return longestContigLength.get();
    }

    @JsonProperty("totalDividedVariantReads")
    public double getTotalDividedVariantReads() {
        return dividedReads.get();
    }

    @JsonProperty("assemblePrematureTerminationEvents")
    public double getAssemblePrematureTerminationEvents() {
        return assemblePrematureTerminationCount.get();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Initial clonotype count", getInitialCloneCount())
                .writePercentAndAbsoluteField("Final clonotype count", getFinalCloneCount(), getInitialCloneCount())
                .writePercentAndAbsoluteField("Canceled assemblies", getCanceledAssemblies(), getInitialCloneCount())
                .writePercentAndAbsoluteField("Number of premature termination assembly events, percent of number of initial clonotypes", getAssemblePrematureTerminationEvents(), getInitialCloneCount())
                .writeField("Longest contig length", getLongestContigLength())
                .writePercentAndAbsoluteField("Clustered variants", getClonesClustered(), getFinalCloneCount() + getClonesClustered())
                .writePercentAndAbsoluteField("Reads in clustered variants", getReadsClustered(), getTotalReads())
                .writePercentAndAbsoluteField("Reads in divided (newly created) clones", getTotalDividedVariantReads(), getTotalReads())
                // .writePercentAndAbsoluteField("Clones with ambiguous letters", getClonesWithAmbiguousLetters(), getFinalCloneCount())
                .writePercentAndAbsoluteField("Clones with ambiguous letters in splitting region", getClonesWithAmbiguousLettersInSplittingRegion(), getFinalCloneCount())
                // .writePercentAndAbsoluteField("Reads in clones with ambiguous letters", getReadsWithAmbiguousLetters(), getTotalReads())
                .writePercentAndAbsoluteField("Reads in clones with ambiguous letters in splitting region", getReadsWithAmbiguousLettersInSplittingRegion(), getTotalReads())
                // .writeField("Average number of ambiguous letters per clone with ambiguous letters", 1.0 * getTotalAmbiguousLetters() / getClonesWithAmbiguousLetters())
                .writeField("Average number of ambiguous letters per clone with ambiguous letters in splitting region", 1.0 * getTotalAmbiguousLettersInSplittingRegion() / getClonesWithAmbiguousLettersInSplittingRegion());
    }
}
