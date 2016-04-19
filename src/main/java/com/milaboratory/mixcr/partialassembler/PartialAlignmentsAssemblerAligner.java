package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.AlignmentResult;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBase;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerAbstract;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerPVFirst;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;

import java.util.EnumMap;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class PartialAlignmentsAssemblerAligner extends VDJCAlignerAbstract<VDJCMultiRead> {
    public PartialAlignmentsAssemblerAligner(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VDJCAlignmentResult<VDJCMultiRead> process(VDJCMultiRead input) {
        ensureInitialized();

        final int nReads = input.numberOfReads();
        EnumMap<GeneType, AlignmentHit<NucleotideSequence, Allele>[][]> allHits = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.VJC_REFERENCE)
            allHits.put(gt, new AlignmentHit[nReads][]);

        NSequenceWithQuality[] targets = new NSequenceWithQuality[nReads];
        int dGeneTarget = -1;
        for (int i = 0; i < nReads; i++) {
            targets[i] = input.getRead(i).getData();
            EnumMap<GeneType, AlignmentResult<AlignmentHit<NucleotideSequence, Allele>>> alignments = new EnumMap<>(GeneType.class);

            int pointer = 0;
            final NucleotideSequence sequence = input.getRead(i).getData().getSequence();
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                AlignmentResult<AlignmentHit<NucleotideSequence, Allele>> als = null;
                if (input.expectedGeneTypes[i].contains(gt)) {
                    final BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> aligner = getAligner(gt);
                    if (aligner != null) {
                        als = aligner.align(sequence, pointer, sequence.size());
                        if (als != null && als.hasHits()) {
                            pointer = als.getBestHit().getAlignment().getSequence2Range().getTo();
                            alignments.put(gt, als);
                        }
                    }
                }

                allHits.get(gt)[i] = als == null ? new AlignmentHit[0] : als.getHits().toArray(new AlignmentHit[als.getHits().size()]);
            }

            if (alignments.get(GeneType.Variable) != null && alignments.get(GeneType.Joining) != null)
                dGeneTarget = i;
        }

        VDJCHit[] vResult = VDJCAlignerPVFirst.combine(parameters.getFeatureToAlign(GeneType.Variable), allHits.get(GeneType.Variable));
        VDJCHit[] jResult = VDJCAlignerPVFirst.combine(parameters.getFeatureToAlign(GeneType.Joining), allHits.get(GeneType.Joining));
        VDJCHit[] dResult;
        if (dGeneTarget == -1)
            dResult = new VDJCHit[0];
        else {
            final Alignment<NucleotideSequence> vAl = vResult[0].getAlignment(dGeneTarget);
            final Alignment<NucleotideSequence> jAl = jResult[0].getAlignment(dGeneTarget);
            if (vAl == null || jAl == null)
                dResult = new VDJCHit[0];
            else
                dResult = singleDAligner.align(targets[dGeneTarget].getSequence(), getPossibleDLoci(vResult, jResult),
                        vAl.getSequence2Range().getTo(),
                        jAl.getSequence2Range().getFrom(),
                        dGeneTarget, nReads);
        }

        final VDJCAlignments alignment = new VDJCAlignments(input.getId(),
                vResult,
                dResult,
                jResult,
                VDJCAlignerPVFirst.combine(parameters.getFeatureToAlign(GeneType.Constant), allHits.get(GeneType.Constant)),
                targets
        );
        return new VDJCAlignmentResult<>(input, alignment);
    }
}
