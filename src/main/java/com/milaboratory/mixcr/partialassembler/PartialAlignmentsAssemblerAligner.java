package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.AlignmentResult;
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
        final int nReads = input.numberOfReads();
        AlignmentHit<NucleotideSequence, Allele>[][] allVHits = new AlignmentHit[nReads][];
        AlignmentHit<NucleotideSequence, Allele>[][] allJHits = new AlignmentHit[nReads][];
        AlignmentHit<NucleotideSequence, Allele>[][] allCHits = new AlignmentHit[nReads][];
        VDJCHit[] dHits = null;
        NSequenceWithQuality[] targets = new NSequenceWithQuality[nReads];
        for (int i = 0; i < nReads; i++) {
            targets[i] = input.getRead(i).getData();
            AlignmentResult<AlignmentHit<NucleotideSequence, Allele>>
                    vAlignments = null,
                    jAlignments = null,
                    cAlignments = null;

            int pointer = 0;

            final NucleotideSequence sequence = input.getRead(i).getData().getSequence();
            if (input.expectedGeneTypes[i].contains(GeneType.Variable)) {
                vAlignments = vAligner.align(sequence);
                if (vAlignments != null && vAlignments.hasHits())
                    pointer = vAlignments.getBestHit().getAlignment().getSequence2Range().getTo();

                allVHits[i] = vAlignments == null ? new AlignmentHit[0] :
                        vAlignments.getHits().toArray(new AlignmentHit[vAlignments.getHits().size()]);
            }

            if (input.expectedGeneTypes[i].contains(GeneType.Joining)) {
                jAlignments = jAligner.align(sequence, pointer, sequence.size());
                if (jAlignments != null && jAlignments.hasHits())
                    pointer = jAlignments.getBestHit().getAlignment().getSequence2Range().getTo();

                allJHits[i] = jAlignments == null ? new AlignmentHit[0] :
                        jAlignments.getHits().toArray(new AlignmentHit[jAlignments.getHits().size()]);
            }

            if (input.expectedGeneTypes[i].contains(GeneType.Constant)) {
                cAlignments = cAligner.align(sequence, pointer, sequence.size());

                allCHits[i] = cAlignments == null ? new AlignmentHit[0] :
                        cAlignments.getHits().toArray(new AlignmentHit[cAlignments.getHits().size()]);
            }


            if (dHits == null && vAlignments != null && vAlignments.hasHits() && jAlignments != null && jAlignments.hasHits()) {
                dHits = singleDAligner.align(sequence, null,
                        vAlignments.getBestHit().getAlignment().getSequence2Range().getTo(),
                        jAlignments.getBestHit().getAlignment().getSequence2Range().getFrom(),
                        i, nReads);
            }
        }

        if (dHits == null)
            dHits = new VDJCHit[0];

        final VDJCAlignments alignment = new VDJCAlignments(0,
                VDJCAlignerPVFirst.combine(parameters.getFeatureToAlign(GeneType.Variable), allVHits),
                dHits,
                VDJCAlignerPVFirst.combine(parameters.getFeatureToAlign(GeneType.Joining), allJHits),
                VDJCAlignerPVFirst.combine(parameters.getFeatureToAlign(GeneType.Constant), allCHits),
                targets
        );
        return new VDJCAlignmentResult<>(input, alignment);
    }
}
