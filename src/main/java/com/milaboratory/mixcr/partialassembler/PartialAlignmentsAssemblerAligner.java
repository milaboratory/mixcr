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
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;

import java.util.EnumMap;

import static com.milaboratory.mixcr.vdjaligners.VDJCAlignerPVFirst.combine;

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
    protected VDJCAlignmentResult<VDJCMultiRead> process0(VDJCMultiRead input) {
        ensureInitialized();

        final int nReads = input.numberOfReads();
        EnumMap<GeneType, VDJCHit[]> vdjcHits = new EnumMap<>(GeneType.class);

        NSequenceWithQuality[] targets = new NSequenceWithQuality[nReads];

        for (int g = 0; g < GeneType.VJC_REFERENCE.length; g++) {
            GeneType gt = GeneType.VJC_REFERENCE[g];
            AlignmentHit[][] alignmentHits = new AlignmentHit[nReads][];
            for (int i = 0; i < nReads; i++) {
                alignmentHits[i] = new AlignmentHit[0];

                targets[i] = input.getRead(i).getData();

                final NucleotideSequence sequence = input.getRead(i).getData().getSequence();

                AlignmentResult<AlignmentHit<NucleotideSequence, Allele>> als;
                if (input.expectedGeneTypes[i].contains(gt)) {
                    final BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> aligner = getAligner(gt);
                    if (aligner != null) {
                        int pointer = 0;
                        if (g != 0) {
                            VDJCHit[] vdjcHits1 = vdjcHits.get(GeneType.VJC_REFERENCE[g - 1]);
                            Alignment<NucleotideSequence> alignment;
                            if (vdjcHits1.length != 0 && (alignment = vdjcHits1[0].getAlignment(i)) != null)
                                pointer = alignment.getSequence2Range().getTo();
                        }
                        als = aligner.align(sequence, pointer, sequence.size());
                        if (als != null && als.hasHits())
                            alignmentHits[i] = als.getHits().toArray(new AlignmentHit[als.getHits().size()]);
                    }
                }
            }
            vdjcHits.put(gt, combine(parameters.getFeatureToAlign(gt), alignmentHits));
        }

        int dGeneTarget = -1;
        VDJCHit[] vResult = vdjcHits.get(GeneType.Variable);
        VDJCHit[] jResult = vdjcHits.get(GeneType.Joining);
        if (vResult.length != 0 && jResult.length != 0)
            for (int i = 0; i < nReads; i++)
                if (vResult[0].getAlignment(i) != null && jResult[0].getAlignment(i) != null) {
                    dGeneTarget = i;
                    break;
                }

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
                vdjcHits.get(GeneType.Constant),
                targets
        );
        return new VDJCAlignmentResult<>(input, alignment);
    }
}
