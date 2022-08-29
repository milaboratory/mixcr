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
package com.milaboratory.mixcr.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsUtil;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.AminoAcidSequence.AminoAcidSequencePosition;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagValue;
import com.milaboratory.util.GlobalObjectMappers;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TObjectFloatHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.SequencePartitioning;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FieldExtractors {
    static final String NULL = "";
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#.#");
    private static final int MAX_SHIFTED_TRIPLETS = 3;

    static Field[] descriptors = null;

    public synchronized static Field[] getFields() {
        if (descriptors == null) {
            List<Field> descriptorsList = new ArrayList<>();

            // Number of targets
            descriptorsList.add(new PL_O("-targets", "Export number of targets", "Number of targets", "numberOfTargets") {
                @Override
                protected String extract(VDJCObject object) {
                    return Integer.toString(object.numberOfTargets());
                }
            });

            // Best hits
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Hit",
                        "Export best " + l + " hit", "Best " + l + " hit", "best" + l + "Hit") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit bestHit = object.getBestHit(type);
                        if (bestHit == null)
                            return NULL;
                        return bestHit.getGene().getName();
                    }
                });
            }

            // Best gene
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Gene",
                        "Export best " + l + " hit gene name (e.g. TRBV12-3 for TRBV12-3*00)", "Best " + l + " gene", "best" + l + "Gene") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit bestHit = object.getBestHit(type);
                        if (bestHit == null)
                            return NULL;
                        return bestHit.getGene().getGeneName();
                    }
                });
            }

            // Best family
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Family",
                        "Export best " + l + " hit family name (e.g. TRBV12 for TRBV12-3*00)", "Best " + l + " family", "best" + l + "Family") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit bestHit = object.getBestHit(type);
                        if (bestHit == null)
                            return NULL;
                        return bestHit.getGene().getFamilyName();
                    }
                });
            }

            // Best hit score
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "HitScore",
                        "Export score for best " + l + " hit", "Best " + l + " hit score", "best" + l + "HitScore") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit bestHit = object.getBestHit(type);
                        if (bestHit == null)
                            return NULL;
                        return String.valueOf(bestHit.getScore());
                    }
                });
            }

            // All hits
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "HitsWithScore",
                        "Export all " + l + " hits with score", "All " + l + " hits with score", "all" + l + "HitsWithScore") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits.length == 0)
                            return "";
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; ; i++) {
                            sb.append(hits[i].getGene().getName())
                                    .append("(").append(SCORE_FORMAT.format(hits[i].getScore()))
                                    .append(")");
                            if (i == hits.length - 1)
                                break;
                            sb.append(",");
                        }
                        return sb.toString();
                    }
                });
            }

            // All hits without score
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Hits",
                        "Export all " + l + " hits", "All " + l + " Hits", "all" + l + "Hits") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits.length == 0)
                            return "";
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; ; i++) {
                            sb.append(hits[i].getGene().getName());
                            if (i == hits.length - 1)
                                break;
                            sb.append(",");
                        }
                        return sb.toString();
                    }
                });
            }

            // All gene names
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new StringExtractor("-" + Character.toLowerCase(l) + "Genes",
                        "Export all " + l + " gene names (e.g. TRBV12-3 for TRBV12-3*00)", "All " + l + " genes", "all" + l + "Genes", type) {
                    @Override
                    String extractStringForHit(VDJCHit hit) {
                        return hit.getGene().getGeneName();
                    }
                });
            }

            // All families
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new StringExtractor("-" + Character.toLowerCase(l) + "Families",
                        "Export all " + l + " gene family anmes (e.g. TRBV12 for TRBV12-3*00)", "All " + l + " families", "all" + l + "Families", type) {
                    @Override
                    String extractStringForHit(VDJCHit hit) {
                        return hit.getGene().getFamilyName();
                    }
                });
            }

            // Best alignment
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Alignment",
                        "Export best " + l + " alignment", "Best " + l + " alignment", "best" + l + "Alignment") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit bestHit = object.getBestHit(type);
                        if (bestHit == null)
                            return NULL;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; ; i++) {
                            Alignment<NucleotideSequence> alignment = bestHit.getAlignment(i);
                            if (alignment == null)
                                sb.append(NULL);
                            else
                                sb.append(alignment.toCompactString());
                            if (i == object.numberOfTargets() - 1)
                                break;
                            sb.append(",");
                        }
                        return sb.toString();
                    }
                });
            }

            // All alignments
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                descriptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Alignments",
                        "Export all " + l + " alignments", "All " + l + " alignments", "all" + l + "Alignments") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits.length == 0)
                            return "";
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; ; ++j) {
                            for (int i = 0; ; i++) {
                                Alignment<NucleotideSequence> alignment = hits[j].getAlignment(i);
                                if (alignment == null)
                                    sb.append(NULL);
                                else
                                    sb.append(alignment.toCompactString());
                                if (i == object.numberOfTargets() - 1)
                                    break;
                                sb.append(',');
                            }
                            if (j == hits.length - 1)
                                break;
                            sb.append(';');
                        }
                        return sb.toString();
                    }
                });
            }

            descriptorsList.add(new FeatureExtractors.NSeqExtractor("-nFeature", "Export nucleotide sequence of specified gene feature", "N. Seq. ", "nSeq") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return seq.getSequence().toString();
                }
            });

            descriptorsList.add(new FeatureExtractors.NSeqExtractor("-qFeature", "Export quality string of specified gene feature", "Qual. ", "qual") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return seq.getQuality().toString();
                }
            });

            descriptorsList.add(new FeatureExtractors.WithHeader("-aaFeature", "Export amino acid sequence of specified gene feature",
                    1, new String[]{"AA. Seq. "}, new String[]{"aaSeq"}) {
                @Override
                protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
                    GeneFeature geneFeature = parameters[parameters.length - 1];
                    NSequenceWithQuality feature = object.getFeature(geneFeature);
                    if (feature == null)
                        return NULL;
                    int targetId = object.getTargetContainingFeature(geneFeature);
                    TranslationParameters tr = targetId == -1 ?
                            TranslationParameters.FromLeftWithIncompleteCodon
                            : object.getPartitionedTarget(targetId).getPartitioning().getTranslationParameters(geneFeature);
                    if (tr == null)
                        return NULL;
                    return AminoAcidSequence.translate(feature.getSequence(), tr).toString();
                }
            });

            descriptorsList.add(new FeatureExtractors.WithHeader("-nFeatureImputed",
                    "Export nucleotide sequence of specified gene feature using letters from germline (marked lowercase) for uncovered regions",
                    1, new String[]{"N. Inc. Seq. "}, new String[]{"nSeqImputed"}) {
                @Override
                protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
                    GeneFeature geneFeature = parameters[parameters.length - 1];
                    VDJCObject.CaseSensitiveNucleotideSequence feature = object.getIncompleteFeature(geneFeature);
                    if (feature == null)
                        return NULL;
                    return feature.toString();
                }
            });

            descriptorsList.add(new FeatureExtractors.WithHeader("-aaFeatureImputed",
                    "Export amino acid sequence of specified gene feature using letters from germline (marked lowercase) for uncovered regions",
                    1, new String[]{"AA. Inc. Seq. "}, new String[]{"aaSeqImputed"}) {
                @Override
                protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
                    GeneFeature geneFeature = parameters[parameters.length - 1];
                    VDJCObject.CaseSensitiveNucleotideSequence feature = object.getIncompleteFeature(geneFeature);
                    if (feature == null)
                        return NULL;
                    String aaStr = feature.toAminoAcidString();
                    if (aaStr == null)
                        return NULL;
                    return aaStr;
                }
            });

            descriptorsList.add(new FeatureExtractors.NSeqExtractor("-minFeatureQuality", "Export minimal quality of specified gene feature", "Min. qual. ", "minQual") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return "" + seq.getQuality().minValue();
                }
            });

            descriptorsList.add(new FeatureExtractors.NSeqExtractor("-avrgFeatureQuality", "Export average quality of specified gene feature", "Mean. qual. ", "meanQual") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return "" + seq.getQuality().meanValue();
                }
            });

            descriptorsList.add(new FeatureExtractors.NSeqExtractor("-lengthOf", "Export length of specified gene feature.", "Length of ", "lengthOf") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return "" + seq.size();
                }
            });

            descriptorsList.add(new FeatureExtractors.MutationsExtractor("-nMutations",
                    "Extract nucleotide mutations for specific gene feature; relative to germline sequence.", 1,
                    new String[]{"N. Mutations in "}, new String[]{"nMutations"}) {
                @Override
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1,
                               NucleotideSequence seq2, Range range, TranslationParameters tr) {
                    return mutations.encode(",");
                }
            });

            descriptorsList.add(new FeatureExtractors.MutationsExtractor("-nMutationsRelative",
                    "Extract nucleotide mutations for specific gene feature relative to another feature.", 2,
                    new String[]{"N. Mutations in ", " relative to "}, new String[]{"nMutationsIn", "Relative"}) {
                @Override
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1,
                               NucleotideSequence seq2, Range range, TranslationParameters tr) {
                    return mutations.encode(",");
                }
            });

            final class AAMutations extends FeatureExtractors.MutationsExtractor {
                AAMutations(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
                    super(command, description, nArgs, hPrefix, sPrefix);
                }

                @Override
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1,
                               NucleotideSequence seq2, Range range, TranslationParameters tr) {
                    if (tr == null) return "-";
                    Mutations<AminoAcidSequence> aaMuts = MutationsUtil.nt2aa(seq1, mutations, tr, MAX_SHIFTED_TRIPLETS);
                    if (aaMuts == null)
                        return "-";

                    AminoAcidSequencePosition aaPos = AminoAcidSequence.convertNtPositionToAA(range.getFrom(), seq1.size(), tr);
                    if (aaPos == null)
                        return "-";
                    int aaFromP1 = aaPos.floor();
                    aaPos = AminoAcidSequence.convertNtPositionToAA(range.getTo(), seq1.size(), tr);
                    if (aaPos == null)
                        return "-";
                    int aaToP1 = aaPos.ceil();

                    aaMuts = aaMuts.extractAbsoluteMutationsForRange(aaFromP1, aaToP1);

                    return aaMuts.encode(",");
                }
            }

            descriptorsList.add(new AAMutations("-aaMutations",
                    "Extract amino acid mutations for specific gene feature", 1,
                    new String[]{"AA. Mutations in "}, new String[]{"aaMutations"}));

            descriptorsList.add(new AAMutations("-aaMutationsRelative",
                    "Extract amino acid mutations for specific gene feature relative to another feature.", 2,
                    new String[]{"AA. Mutations in ", " relative to "}, new String[]{"aaMutationsIn", "Relative"}));

            final class MutationsDetailed extends FeatureExtractors.MutationsExtractor {
                MutationsDetailed(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
                    super(command, description, nArgs, hPrefix, sPrefix);
                }

                @Override
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1, NucleotideSequence seq2, Range range, TranslationParameters tr) {
                    if (tr == null) return "-";
                    MutationsUtil.MutationNt2AADescriptor[] descriptors = MutationsUtil.nt2aaDetailed(seq1, mutations, tr, MAX_SHIFTED_TRIPLETS);
                    if (descriptors == null)
                        return "-";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < descriptors.length; i++) {
                        sb.append(descriptors[i]);
                        if (i == descriptors.length - 1)
                            break;
                        sb.append(",");
                    }
                    return sb.toString();
                }
            }

            String detailedMutationsFormat =
                    "Format <nt_mutation>:<aa_mutation_individual>:<aa_mutation_cumulative>, where <aa_mutation_individual> is an expected amino acid " +
                            "mutation given no other mutations have occurred, and <aa_mutation_cumulative> amino acid mutation is the observed amino acid " +
                            "mutation combining effect from all other. WARNING: format may change in following versions.";
            descriptorsList.add(new MutationsDetailed("-mutationsDetailed",
                    "Detailed list of nucleotide and corresponding amino acid mutations. " + detailedMutationsFormat, 1,
                    new String[]{"Detailed mutations in "}, new String[]{"mutationsDetailedIn"}));

            descriptorsList.add(new MutationsDetailed("-mutationsDetailedRelative",
                    "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. " + detailedMutationsFormat, 2,
                    new String[]{"Detailed mutations in ", " relative to "}, new String[]{"mutationsDetailedIn", "Relative"}));

            descriptorsList.add(new ExtractReferencePointPosition(true));
            descriptorsList.add(new ExtractReferencePointPosition(false));

            descriptorsList.add(new ExtractDefaultReferencePointsPositions());

            descriptorsList.add(new PL_A("-readId", "Export id of read corresponding to alignment (deprecated)", "Read id", "readId") {
                @Override
                protected String extract(VDJCAlignments object) {
                    return "" + object.getMinReadId();
                }

                @Override
                public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
                    System.out.println("WARNING: -readId is deprecated. Use -readIds");
                    return super.create(outputMode, headerData, args);
                }
            });

            descriptorsList.add(new PL_A("-readIds", "Export id(s) of read(s) corresponding to alignment", "Read id", "readId") {
                @Override
                protected String extract(VDJCAlignments object) {
                    long[] readIds = object.getReadIds();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; ; i++) {
                        sb.append(readIds[i]);
                        if (i == readIds.length - 1)
                            return sb.toString();
                        sb.append(",");
                    }
                }
            });

            descriptorsList.add(new PL_C("-cloneId", "Unique clone identifier", "Clone ID", "cloneId") {
                @Override
                protected String extract(Clone object) {
                    return "" + object.getId();
                }
            });

            descriptorsList.add(new PL_C("-count", "Export clone count", "Clone count", "cloneCount") {
                @Override
                protected String extract(Clone object) {
                    return "" + object.getCount();
                }
            });

            descriptorsList.add(new PL_C("-fraction", "Export clone fraction", "Clone fraction", "cloneFraction") {
                @Override
                protected String extract(Clone object) {
                    return "" + object.getFraction();
                }
            });

            descriptorsList.add(new ExtractSequence(VDJCObject.class, "-targetSequences",
                    "Export aligned sequences (targets), separated with comma",
                    "Target sequences", "targetSequences"));

            descriptorsList.add(new ExtractSequenceQuality(VDJCObject.class, "-targetQualities",
                    "Export aligned sequence (target) qualities, separated with comma",
                    "Target sequence qualities", "targetQualities"));

            descriptorsList.add(new PL_A("-descrR1", "Export description line from initial .fasta or .fastq file (deprecated)", "Description R1", "descrR1") {
                @Override
                protected String extract(VDJCAlignments object) {
                    List<SequenceRead> reads = object.getOriginalReads();
                    if (reads == null)
                        throw new IllegalArgumentException("Error for option \'-descrR1\':\n" +
                                "No description available for read: either re-run align action with -OsaveOriginalReads=true option " +
                                "or don't use \'-descrR1\' in exportAlignments");

                    return reads.get(0).getRead(0).getDescription();
                }

                @Override
                public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
                    System.out.println("WARNING: -descrR1 is deprecated. Use -descrsR1");
                    return super.create(outputMode, headerData, args);
                }
            });

            descriptorsList.add(new PL_A("-descrR2", "Export description line from initial .fasta or .fastq file (deprecated)", "Description R2", "descrR2") {
                @Override
                protected String extract(VDJCAlignments object) {
                    List<SequenceRead> reads = object.getOriginalReads();
                    if (reads == null)
                        throw new IllegalArgumentException("Error for option \'-descrR1\':\n" +
                                "No description available for read: either re-run align action with -OsaveOriginalReads=true option " +
                                "or don't use \'-descrR1\' in exportAlignments");
                    SequenceRead read = reads.get(0);
                    if (read.numberOfReads() < 2)
                        throw new IllegalArgumentException("Error for option \'-descrR2\':\n" +
                                "No description available for second read: your input data was single-end");
                    return read.getRead(1).getDescription();
                }

                @Override
                public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
                    System.out.println("WARNING: -descrR2 is deprecated. Use -descrsR2");
                    return super.create(outputMode, headerData, args);
                }
            });


            descriptorsList.add(new PL_A("-descrsR1", "Export description lines from initial .fasta or .fastq file " +
                    "for R1 reads (only available if -OsaveOriginalReads=true was used in align command)", "Descriptions R1", "descrsR1") {
                @Override
                protected String extract(VDJCAlignments object) {
                    List<SequenceRead> reads = object.getOriginalReads();
                    if (reads == null)
                        throw new IllegalArgumentException("Error for option \'-descrR1\':\n" +
                                "No description available for read: either re-run align action with -OsaveOriginalReads option " +
                                "or don't use \'-descrR1\' in exportAlignments");

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; ; i++) {
                        sb.append(reads.get(i).getRead(0).getDescription());
                        if (i == reads.size() - 1)
                            return sb.toString();
                        sb.append(",");
                    }
                }
            });

            descriptorsList.add(new PL_A("-descrsR2", "Export description lines from initial .fastq file " +
                    "for R2 reads (only available if -OsaveOriginalReads=true was used in align command)", "Descriptions R2", "descrsR2") {
                @Override
                protected String extract(VDJCAlignments object) {
                    List<SequenceRead> reads = object.getOriginalReads();
                    if (reads == null)
                        throw new IllegalArgumentException("Error for option \'-descrR1\':\n" +
                                "No description available for read: either re-run align action with -OsaveOriginalReads option " +
                                "or don't use \'-descrR1\' in exportAlignments");

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; ; i++) {
                        SequenceRead read = reads.get(i);
                        if (read.numberOfReads() < 2)
                            throw new IllegalArgumentException("Error for option \'-descrsR2\':\n" +
                                    "No description available for second read: your input data was single-end");
                        sb.append(read.getRead(1).getDescription());
                        if (i == reads.size() - 1)
                            return sb.toString();
                        sb.append(",");
                    }
                }
            });

            descriptorsList.add(new PL_A("-readHistory", "Export read history", "Read history", "readHistory") {
                @Override
                protected String extract(VDJCAlignments object) {
                    try {
                        return GlobalObjectMappers.toOneLine(object.getHistory());
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });

            descriptorsList.add(new PL_A("-cloneId", "To which clone alignment was attached (make sure using .clna file as input for exportAlignments)", "Clone ID", "cloneId") {
                @Override
                protected String extract(VDJCAlignments object) {
                    return "" + object.getCloneIndex();
                }
            });

            descriptorsList.add(new PL_A("-cloneIdWithMappingType", "To which clone alignment was attached with additional info on mapping type (make sure using .clna file as input for exportAlignments)", "Clone mapping", "cloneMapping") {
                @Override
                protected String extract(VDJCAlignments object) {
                    long ci = object.getCloneIndex();
                    ReadToCloneMapping.MappingType mt = object.getMappingType();
                    return "" + ci + ":" + mt;

                }
            });

            // descriptorsList.add(alignmentsToClone("-cloneId", "To which clone alignment was attached.", false));
            // descriptorsList.add(alignmentsToClone("-cloneIdWithMappingType", "To which clone alignment was attached with additional info on mapping type.", true));
            // descriptorsList.add(new AbstractField<Clone>(Clone.class, "-readIds", "Read IDs aggregated by clone.") {
            //     @Override
            //     public FieldExtractor<Clone> create(OutputMode outputMode, String[] args) {
            //         return new CloneToReadsExtractor(outputMode, args[0]);
            //     }
            //
            //     @Override
            //     public String metaVars() {
            //         return "<index_file>";
            //     }
            // });

            for (final GeneType type : GeneType.values()) {
                String c = Character.toLowerCase(type.getLetter()) + "IdentityPercents";
                descriptorsList.add(new PL_O("-" + c, type.getLetter() + " alignment identity percents",
                        type.getLetter() + " alignment identity percents", c) {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits == null || hits.length == 0)
                            return NULL;
                        StringBuilder sb = new StringBuilder();
                        sb.append("");
                        for (int i = 0; ; i++) {
                            sb.append(hits[i].getIdentity());
                            if (i == hits.length - 1)
                                return sb.toString();
                            sb.append(",");
                        }
                    }
                });
            }
            for (final GeneType type : GeneType.values()) {
                String c = Character.toLowerCase(type.getLetter()) + "BestIdentityPercent";
                descriptorsList.add(new PL_O("-" + c, type.getLetter() + " best alignment identity percent",
                        type.getLetter() + "best alignment identity percent", c) {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit hit = object.getBestHit(type);
                        if (hit == null)
                            return NULL;
                        return Float.toString(hit.getIdentity());
                    }
                });
            }

            descriptorsList.add(new PL_O("-chains", "Chains", "Chains", "chains") {
                @Override
                protected String extract(VDJCObject object) {
                    return object.commonChains().toString();
                }
            });

            descriptorsList.add(new PL_O("-topChains", "Top chains", "Top chains", "topChains") {
                @Override
                protected String extract(VDJCObject object) {
                    return object.commonTopChains().toString();
                }
            });

            descriptorsList.add(new PL_O("-tagCounts", "All tags with counts", "All tags counts", "taqCounts") {
                @Override
                protected String extract(VDJCObject object) {
                    return object.getTagCount().toString();
                }
            });

            descriptorsList.add(new PL_C("-tagFractions", "All tags with fractions", "All tags", "taqFractions") {
                @Override
                protected String extract(Clone object) {
                    return object.getTagFractions().toString();
                }
            });

            descriptorsList.add(new AbstractField<VDJCObject>(VDJCObject.class, "-tag",
                    "Tag value (i.e. CELL barcode or UMI sequence)") {
                @Override
                public int nArguments() {
                    return 1;
                }

                @Override
                public String metaVars() {
                    return "tag_name";
                }

                private String getHeader(OutputMode outputMode, String name) {
                    switch (outputMode) {
                        case HumanFriendly:
                            return "Tag Value " + name;
                        case ScriptingFriendly:
                            return "tagValue" + name;
                    }
                    throw new RuntimeException();
                }

                @Override
                public FieldExtractor<VDJCObject> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
                    String tagName = args[0];
                    int idx = headerData.getTagsInfo().indexOf(tagName);
                    if (idx == -1)
                        throw new IllegalArgumentException("No tag with name " + tagName);
                    return new AbstractFieldExtractor<VDJCObject>(getHeader(outputMode, tagName), this) {
                        @Override
                        public String extractValue(VDJCObject object) {
                            TagValue tagValue = object.getTagCount().singleOrNull(idx);
                            if (tagValue == null)
                                return NULL;
                            return tagValue.toString();
                        }
                    };
                }
            });

            descriptorsList.add(new AbstractField<VDJCObject>(VDJCObject.class, "-uniqueTagCount", "Unique tag count") {
                @Override
                public int nArguments() {
                    return 1;
                }

                @Override
                public String metaVars() {
                    return "tag_names";
                }

                private String getHeader(OutputMode outputMode, String name) {
                    switch (outputMode) {
                        case HumanFriendly:
                            return "Unique Tag Count " + name;
                        case ScriptingFriendly:
                            return "uniqueTagCount" + name;
                    }
                    throw new RuntimeException();
                }

                @Override
                public FieldExtractor<VDJCObject> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
                    String tagName = args[0];

                    int idx = headerData.getTagsInfo().indexOf(tagName);
                    if (idx == -1)
                        throw new IllegalArgumentException("No tag with name " + tagName);

                    int level = idx + 1;

                    return new AbstractFieldExtractor<VDJCObject>(getHeader(outputMode, tagName), this) {
                        @Override
                        public String extractValue(VDJCObject object) {
                            return "" + object.getTagCount().reduceToLevel(level).size();
                        }
                    };
                }
            });

            descriptorsList.add(new PL_C("-cellGroup", "Cell group", "Cell group", "cellGroup") {
                @Override
                protected String extract(Clone object) {
                    return String.valueOf(object.getGroup());
                }
            });

            descriptors = descriptorsList.toArray(new Field[descriptorsList.size()]);
        }

        return descriptors;
    }

    public static boolean hasField(String name) {
        for (Field field : getFields())
            if (name.equalsIgnoreCase(field.getCommand()))
                return true;
        return false;
    }

    // public static FieldExtractor parse(OutputMode outputMode, Class clazz, String[] args) {
    //     for (Field field : getFields())
    //         if (field.canExtractFrom(clazz) && args[0].equalsIgnoreCase(field.getCommand()))
    //             return field.create(outputMode, , Arrays.copyOfRange(args, 1, args.length));
    //     throw new IllegalArgumentException("Not a valid options: " + Arrays.toString(args));
    // }

    public static ArrayList<String>[] getDescription(Class clazz) {
        ArrayList<String>[] description = new ArrayList[]{new ArrayList(), new ArrayList()};
        for (Field field : getFields())
            if (field.canExtractFrom(clazz)) {
                description[0].add(field.getCommand() + " " + field.metaVars());
                description[1].add(field.getDescription());
            }
        return description;
    }

    private static ArrayList<String>[] getDescriptionsForSpecificClassOnly(boolean clones) {
        ArrayList<String>[] description = new ArrayList[]{new ArrayList(), new ArrayList()};
        for (Field field : getFields()) {
            boolean c;
            if (clones)
                c = field.canExtractFrom(Clone.class) && !field.canExtractFrom(VDJCAlignments.class);
            else
                c = field.canExtractFrom(VDJCAlignments.class) && !field.canExtractFrom(Clone.class);
            if (c) {
                description[0].add(field.getCommand() + " " + field.metaVars());
                description[1].add(field.getDescription());
            }
        }
        return description;
    }

    static ArrayList<String>[] getDescriptionSpecificForClones() {
        return getDescriptionsForSpecificClassOnly(true);
    }

    static ArrayList<String>[] getDescriptionSpecificForAlignments() {
        return getDescriptionsForSpecificClassOnly(false);
    }

    static ArrayList<String>[] getDescriptionSpecificForClass(Class clazz) {
        if (clazz.equals(VDJCObject.class))
            return getDescription(clazz);
        return getDescriptionsForSpecificClassOnly(clazz.equals(Clone.class));
    }

    /* Some typedefs */
    static abstract class PL_O extends FieldParameterless<VDJCObject> {
        PL_O(String command, String description, String hHeader, String sHeader) {
            super(VDJCObject.class, command, description, hHeader, sHeader);
        }
    }

    static abstract class PL_A extends FieldParameterless<VDJCAlignments> {
        PL_A(String command, String description, String hHeader, String sHeader) {
            super(VDJCAlignments.class, command, description, hHeader, sHeader);
        }
    }

    static abstract class PL_C extends FieldParameterless<Clone> {
        PL_C(String command, String description, String hHeader, String sHeader) {
            super(Clone.class, command, description, hHeader, sHeader);
        }
    }

    static abstract class WP_O<P> extends FieldWithParameters<VDJCObject, P> {
        protected WP_O(String command, String description, int nArguments) {
            super(VDJCObject.class, command, description, nArguments);
        }
    }

    /***************************************************/
    private static class ExtractSequence extends FieldParameterless<VDJCObject> {
        private ExtractSequence(Class targetType, String command, String description, String hHeader, String sHeader) {
            super(targetType, command, description, hHeader, sHeader);
        }

        @Override
        protected String extract(VDJCObject object) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; ; i++) {
                sb.append(object.getTarget(i).getSequence());
                if (i == object.numberOfTargets() - 1)
                    break;
                sb.append(",");
            }
            return sb.toString();
        }
    }

    private static class ExtractSequenceQuality extends FieldParameterless<VDJCObject> {
        private ExtractSequenceQuality(Class targetType, String command, String description, String hHeader, String sHeader) {
            super(targetType, command, description, hHeader, sHeader);
        }

        @Override
        protected String extract(VDJCObject object) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; ; i++) {
                sb.append(object.getTarget(i).getQuality());
                if (i == object.numberOfTargets() - 1)
                    break;
                sb.append(",");
            }
            return sb.toString();
        }
    }

    private static class ExtractReferencePointPosition extends WP_O<ReferencePoint> {
        private final boolean inReference;

        protected ExtractReferencePointPosition(boolean inReference) {
            super(inReference ? "-positionInReferenceOf" : "-positionOf",
                    "Export position of specified reference point inside " + (inReference ? "reference" : "target") + "sequences " +
                            "(clonal sequence / read sequence).", 1);
            this.inReference = inReference;
        }

        @Override
        protected ReferencePoint getParameters(String[] string) {
            if (string.length != 1)
                throw new RuntimeException("Wrong number of parameters for " + getCommand());
            return ReferencePoint.parse(string[0]);
        }

        @Override
        protected String getHeader(OutputMode outputMode, ReferencePoint parameters) {
            return choose(outputMode, "Position " + (inReference ? "in reference" : "") + " of ",
                    inReference ? "positionInReferenceOf" : "positionOf") +
                    ReferencePoint.encode(parameters, true);
        }

        @Override
        protected String extractValue(VDJCObject object, ReferencePoint refPoint) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; ; i++) {
                int positionInTarget = object.getPartitionedTarget(i).getPartitioning().getPosition(refPoint);
                if (inReference) {
                    VDJCHit hit = object.getBestHit(refPoint.getGeneType());
                    if (hit != null) {
                        Alignment<NucleotideSequence> al = hit.getAlignment(i);
                        if (al != null)
                            positionInTarget = al.convertToSeq1Position(positionInTarget);
                    }
                }

                sb.append(positionInTarget);
                if (i == object.numberOfTargets() - 1)
                    break;
                sb.append(",");
            }
            return sb.toString();
        }

        @Override
        public String metaVars() {
            return "<reference_point>";
        }
    }

    static class ExtractDefaultReferencePointsPositions extends PL_O {
        public ExtractDefaultReferencePointsPositions() {
            super("-defaultAnchorPoints", "Outputs a list of default reference points (like CDR2Begin, FR4End, etc. " +
                    "see documentation for the full list and formatting)", "Ref. points", "refPoints");
        }

        @Override
        protected String extract(VDJCObject object) {
            StringBuilder sb = new StringBuilder();
            VDJCHit bestVHit = object.getBestHit(GeneType.Variable);
            VDJCHit bestDHit = object.getBestHit(GeneType.Diversity);
            VDJCHit bestJHit = object.getBestHit(GeneType.Joining);
            for (int i = 0; ; i++) {
                SequencePartitioning partitioning = object.getPartitionedTarget(i).getPartitioning();
                for (int j = 0; ; j++) {
                    ReferencePoint refPoint = ReferencePoint.DefaultReferencePoints[j];

                    // Processing special cases for number of deleted / P-segment nucleotides
                    if (refPoint.equals(ReferencePoint.VEnd))
                        sb.append(trimmedPosition(bestVHit, i, ReferencePoint.VEndTrimmed, ReferencePoint.VEnd));
                    else if (refPoint.equals(ReferencePoint.DBegin))
                        sb.append(trimmedPosition(bestDHit, i, ReferencePoint.DBeginTrimmed, ReferencePoint.DBegin));
                    else if (refPoint.equals(ReferencePoint.DEnd))
                        sb.append(trimmedPosition(bestDHit, i, ReferencePoint.DEndTrimmed, ReferencePoint.DEnd));
                    else if (refPoint.equals(ReferencePoint.JBegin))
                        sb.append(trimmedPosition(bestJHit, i, ReferencePoint.JBeginTrimmed, ReferencePoint.JBegin));

                    else {
                        // Normal points
                        int referencePointPosition = partitioning.getPosition(refPoint);
                        if (referencePointPosition >= 0)
                            sb.append(referencePointPosition);
                    }
                    if (j == ReferencePoint.DefaultReferencePoints.length - 1)
                        break;
                    sb.append(":");
                }
                if (i == object.numberOfTargets() - 1)
                    break;
                sb.append(",");
            }
            return sb.toString();
        }

        public static String trimmedPosition(VDJCHit hit, int targetId, ReferencePoint trimmedPoint, ReferencePoint boundaryPoint) {
            if (!trimmedPoint.isAttachedToAlignmentBound())
                throw new IllegalArgumentException();

            // No hit - no point
            if (hit == null)
                return "";

            Alignment<NucleotideSequence> alignment = hit.getAlignment(targetId);

            // If alignment is not defined for this target
            if (alignment == null)
                return "";

            ReferencePoint ap = trimmedPoint.getActivationPoint();
            Range seq1Range = alignment.getSequence1Range();
            if (ap != null) {
                // Point is valid only if activation point is reached
                int apPositionInSeq1 = hit.getGene().getPartitioning().getRelativePosition(hit.getAlignedFeature(), ap);
                if (apPositionInSeq1 < 0 ||
                        trimmedPoint.isAttachedToLeftAlignmentBound() ?
                        seq1Range.getFrom() > apPositionInSeq1 :
                        seq1Range.getTo() <= apPositionInSeq1)
                    return "";
            }

            int bpPositionInSeq1 = hit.getGene().getPartitioning().getRelativePosition(hit.getAlignedFeature(), boundaryPoint);

            // Just in case
            if (bpPositionInSeq1 < 0)
                return "";

            return Integer.toString(
                    trimmedPoint.isAttachedToLeftAlignmentBound() ?
                            bpPositionInSeq1 - seq1Range.getFrom() :
                            seq1Range.getTo() - bpPositionInSeq1);
        }
    }


    // private static AbstractField<VDJCAlignments> alignmentsToClone(
    //         final String command, final String description, final boolean printMapping) {
    //     return new AbstractField<VDJCAlignments>(VDJCAlignments.class, command, description) {
    //         @Override
    //         public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, String[] args) {
    //             return new AlignmentToCloneExtractor(outputMode, args[0], printMapping);
    //         }
    //
    //         @Override
    //         public String metaVars() {
    //             return "<index_file>";
    //         }
    //     };
    // }

    // private static final class AlignmentToCloneExtractor
    //         implements FieldExtractor<VDJCAlignments>, Closeable {
    //     private final OutputMode outputMode;
    //     private final AlignmentsToClonesMappingContainer container;
    //     private final OutputPort<ReadToCloneMapping> byAls;
    //     private final boolean printMapping;
    //     private final Iterator<ReadToCloneMapping> mappingIterator;
    //     private ReadToCloneMapping currentMapping = null;
    //
    //     public AlignmentToCloneExtractor(OutputMode outputMode, String indexFile, boolean printMapping) {
    //         try {
    //             this.outputMode = outputMode;
    //             this.printMapping = printMapping;
    //             this.container = AlignmentsToClonesMappingContainer.open(indexFile);
    //             this.byAls = this.container.createPortByAlignments();
    //             this.mappingIterator = CUtils.it(byAls).iterator();
    //         } catch (IOException e) {
    //             throw new RuntimeException(e);
    //         }
    //     }
    //
    //     @Override
    //     public String getHeader() {
    //         if (printMapping)
    //             return choose(outputMode, "Clone mapping", "cloneMapping");
    //         else
    //             return choose(outputMode, "Clone Id", "cloneId");
    //     }
    //
    //     @Override
    //     public String extractValue(VDJCAlignments object) {
    //         if (currentMapping == null && mappingIterator.hasNext())
    //             currentMapping = mappingIterator.next();
    //
    //         if (currentMapping == null)
    //             throw new IllegalArgumentException("Wrong number of records in index.");
    //
    //         while (currentMapping.getAlignmentsId() < object.getAlignmentsIndex() && mappingIterator.hasNext())
    //             currentMapping = mappingIterator.next();
    //         if (currentMapping.getAlignmentsId() != object.getAlignmentsIndex())
    //             return printMapping ? Dropped.toString().toLowerCase() : NULL;
    //
    //         int cloneIndex = currentMapping.getCloneIndex();
    //         ReadToCloneMapping.MappingType mt = currentMapping.getMappingType();
    //         if (currentMapping.isDropped())
    //             return printMapping ? mt.toString().toLowerCase() : NULL;
    //         return printMapping ? Integer.toString(cloneIndex) + ":" + mt.toString().toLowerCase() : Integer.toString(cloneIndex);
    //     }
    //
    //     @Override
    //     public void close() throws IOException {
    //         container.close();
    //     }
    // }
    //
    // private static final class CloneToReadsExtractor
    //         implements FieldExtractor<Clone>, Closeable {
    //     private final OutputMode outputMode;
    //     private final AlignmentsToClonesMappingContainer container;
    //     private final Iterator<ReadToCloneMapping> mappingIterator;
    //     private ReadToCloneMapping currentMapping;
    //
    //     public CloneToReadsExtractor(OutputMode outputMode, String file) {
    //         try {
    //             this.outputMode = outputMode;
    //             this.container = AlignmentsToClonesMappingContainer.open(file);
    //             this.mappingIterator = CUtils.it(this.container.createPortByClones()).iterator();
    //         } catch (IOException e) {
    //             throw new RuntimeException(e);
    //         }
    //     }
    //
    //     @Override
    //     public String getHeader() {
    //         return choose(outputMode, "Reads", "reads");
    //     }
    //
    //     @Override
    //     public String extractValue(Clone clone) {
    //         if (currentMapping == null && mappingIterator.hasNext())
    //             currentMapping = mappingIterator.next();
    //
    //         if (currentMapping == null)
    //             throw new IllegalArgumentException("Wrong number of records in index.");
    //
    //         while (currentMapping.getCloneIndex() < clone.getId() && mappingIterator.hasNext())
    //             currentMapping = mappingIterator.next();
    //
    //         long count = 0;
    //         StringBuilder sb = new StringBuilder();
    //         while (currentMapping.getCloneIndex() == clone.getId()) {
    //             ++count;
    //             assert currentMapping.getCloneIndex() == currentMapping.getCloneIndex();
    //             long[] readIds = currentMapping.getReadIds();
    //             for (long readId : readIds)
    //                 sb.append(readId).append(",");
    //             if (!mappingIterator.hasNext())
    //                 break;
    //             currentMapping = mappingIterator.next();
    //         }
    //         //count == object.getCount() only if addReadsCountOnClustering=true
    //         assert count >= clone.getCount() : "Actual count: " + clone.getCount() + ", in mapping: " + count;
    //         if (sb.length() != 0)
    //             sb.deleteCharAt(sb.length() - 1);
    //         return sb.toString();
    //     }
    //
    //     @Override
    //     public void close() throws IOException {
    //         container.close();
    //     }
    // }

    public static String choose(OutputMode outputMode, String hString, String sString) {
        switch (outputMode) {
            case HumanFriendly:
                return hString;
            case ScriptingFriendly:
                return sString;
            default:
                throw new NullPointerException();
        }
    }

    private abstract static class StringExtractor extends PL_O {
        final GeneType type;

        public StringExtractor(String command, String description, String hHeader, String sHeader,
                               GeneType type) {
            super(command, description, hHeader, sHeader);
            this.type = type;
        }

        @Override
        protected String extract(VDJCObject object) {
            TObjectFloatHashMap<String> familyScores = new TObjectFloatHashMap<>();
            VDJCHit[] hits = object.getHits(type);
            if (hits.length == 0)
                return "";

            for (VDJCHit hit : hits) {
                String s = extractStringForHit(hit);
                if (!familyScores.containsKey(s))
                    familyScores.put(s, hit.getScore());
            }

            final Holder[] hs = new Holder[familyScores.size()];
            final TObjectFloatIterator<String> it = familyScores.iterator();
            int i = 0;
            while (it.hasNext()) {
                it.advance();
                hs[i++] = new Holder(it.key(), it.value());
            }

            Arrays.sort(hs);

            StringBuilder sb = new StringBuilder();
            for (i = 0; ; i++) {
                sb.append(hs[i].str);
                if (i == hs.length - 1)
                    break;
                sb.append(",");
            }
            return sb.toString();
        }

        abstract String extractStringForHit(VDJCHit hit);
    }

    private static final class Holder implements Comparable<Holder> {
        final String str;
        final float score;

        public Holder(String str, float score) {
            this.str = str;
            this.score = score;
        }

        @Override
        public int compareTo(Holder o) {
            return Float.compare(o.score, score);
        }
    }
}
