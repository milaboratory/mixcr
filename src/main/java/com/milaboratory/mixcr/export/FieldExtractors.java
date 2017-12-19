/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.export;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsUtil;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.util.GlobalObjectMappers;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TObjectFloatHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.SequencePartitioning;

import java.io.Closeable;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.MappingType.Dropped;

public final class FieldExtractors {
    static final String NULL = "";
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#.#");

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
                        "Export all " + l + " hits with score", "All " + l + " hits", "all" + l + "HitsWithScore") {
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

//            descriptorsList.add(new FeatureExtractorDescriptor("-aaFeatureFromLeft", "Export amino acid sequence of " +
//                    "specified gene feature starting from the leftmost nucleotide (differs from -aaFeature only for " +
//                    "sequences which length are not multiple of 3)", "AA. Seq.", "aaSeq") {
//                @Override
//                public String convert(NSequenceWithQuality seq) {
//                    return AminoAcidSequence.translate(seq.getSequence(), FromLeftWithoutIncompleteCodon).toString();
//                }
//            });
//
//            descriptorsList.add(new FeatureExtractorDescriptor("-aaFeatureFromRight", "Export amino acid sequence of " +
//                    "specified gene feature starting from the rightmost nucleotide (differs from -aaFeature only for " +
//                    "sequences which length are not multiple of 3)", "AA. Seq.", "aaSeq") {
//                @Override
//                public String convert(NSequenceWithQuality seq) {
//                    return AminoAcidSequence.translate(seq.getSequence(), FromRightWithoutIncompleteCodon).toString();
//                }
//            });

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

            descriptorsList.add(new FeatureExtractors.NSeqExtractor("-lengthOf", "Exports length of specified gene feature.", "Length of ", "lengthOf") {
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
                               NucleotideSequence seq2, TranslationParameters tr) {
                    return mutations.encode(",");
                }
            });

            descriptorsList.add(new FeatureExtractors.MutationsExtractor("-nMutationsRelative",
                    "Extract nucleotide mutations for specific gene feature relative to another feature.", 2,
                    new String[]{"N. Mutations in ", " relative to "}, new String[]{"nMutationsIn", "Relative"}) {
                @Override
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1,
                               NucleotideSequence seq2, TranslationParameters tr) {
                    return mutations.encode(",");
                }
            });

            final class AAMutations extends FeatureExtractors.MutationsExtractor {
                AAMutations(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
                    super(command, description, nArgs, hPrefix, sPrefix);
                }

                @Override
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1,
                               NucleotideSequence seq2, TranslationParameters tr) {
                    if (tr == null) return "-";
                    Mutations<AminoAcidSequence> aaMuts = MutationsUtil.nt2aa(seq1, mutations, tr);
                    if (aaMuts == null)
                        return "-";
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
                String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1, NucleotideSequence seq2, TranslationParameters tr) {
                    if (tr == null) return "-";
                    MutationsUtil.MutationNt2AADescriptor[] descriptors = MutationsUtil.nt2aaDetailed(seq1, mutations, tr, 10);
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

            descriptorsList.add(new ExtractReferencePointPosition());

            descriptorsList.add(new ExtractDefaultReferencePointsPositions());

            descriptorsList.add(new PL_A("-readId", "Export id of read corresponding to alignment", "Read id", "readId") {
                @Override
                protected String extract(VDJCAlignments object) {
                    return "" + object.getMinReadId();
                }

                @Override
                public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, String[] args) {
                    System.out.println("WARNING: -readId is deprecated. Use -readIds");
                    return super.create(outputMode, args);
                }
            });

            descriptorsList.add(new PL_A("-readIds", "Export id of read corresponding to alignment", "Read id", "readId") {
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

            descriptorsList.add(new ExtractSequence(VDJCAlignments.class, "-sequence",
                    "Export aligned sequence (initial read), or 2 sequences in case of paired-end reads",
                    "Read(s) sequence", "readSequence"));

            descriptorsList.add(new ExtractSequenceQuality(VDJCAlignments.class, "-quality",
                    "Export initial read quality, or 2 qualities in case of paired-end reads",
                    "Read(s) sequence qualities", "readQuality"));

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

            descriptorsList.add(new ExtractSequence(Clone.class, "-sequence",
                    "Export aligned sequence (initial read), or 2 sequences in case of paired-end reads",
                    "Clonal sequence(s)", "clonalSequence"));

            descriptorsList.add(new ExtractSequenceQuality(Clone.class, "-quality",
                    "Export initial read quality, or 2 qualities in case of paired-end reads",
                    "Clonal sequence quality(s)", "clonalSequenceQuality"));

            descriptorsList.add(new PL_A("-descrR1", "Export description line from initial .fasta or .fastq file " +
                    "of the first read (only available if --save-description was used in align command)", "Description R1", "descrR1") {
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
                public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, String[] args) {
                    System.out.println("WARNING: -descrR1 is deprecated. Use -descrsR1");
                    return super.create(outputMode, args);
                }
            });

            descriptorsList.add(new PL_A("-descrR2", "Export description line from initial .fasta or .fastq file " +
                    "of the second read (only available if --save-description was used in align command)", "Description R2", "descrR2") {
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
                public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, String[] args) {
                    System.out.println("WARNING: -descrR2 is deprecated. Use -descrsR2");
                    return super.create(outputMode, args);
                }
            });


            descriptorsList.add(new PL_A("-descrsR1", "Export description lines from initial .fasta or .fastq file " +
                    "of the first reads (only available if -OsaveOriginalReads=true was used in align command)", "Descriptions R1", "descrsR1") {
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

            descriptorsList.add(new PL_A("-descrsR2", "Export description lines from initial .fasta or .fastq file " +
                    "of the second reads (only available if -OsaveOriginalReads=true was used in align command)", "Descriptions R2", "descrsR2") {
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
                        if (hits == null)
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
                descriptorsList.add(new PL_O("-" + c, type.getLetter() + "best alignment identity percent",
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

            descriptorsList.add(new PL_O("-chains", "Chains", "Chains", "Chains") {
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

            descriptors = descriptorsList.toArray(new Field[descriptorsList.size()]);
        }

        return descriptors;
    }

    public static FieldExtractor parse(OutputMode outputMode, Class clazz, String[] args) {
        for (Field field : getFields())
            if (field.canExtractFrom(clazz) && args[0].equalsIgnoreCase(field.getCommand()))
                return field.create(outputMode, Arrays.copyOfRange(args, 1, args.length));
        throw new IllegalArgumentException("Not a valid options: " + Arrays.toString(args));
    }

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
        protected WP_O(String command, String description) {
            super(VDJCObject.class, command, description);
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
        protected ExtractReferencePointPosition() {
            super("-positionOf",
                    "Exports position of specified reference point inside target sequences " +
                            "(clonal sequence / read sequence).");
        }

        @Override
        protected ReferencePoint getParameters(String[] string) {
            if (string.length != 1)
                throw new RuntimeException("Wrong number of parameters for " + getCommand());
            return ReferencePoint.parse(string[0]);
        }

        @Override
        protected String getHeader(OutputMode outputMode, ReferencePoint parameters) {
            return choose(outputMode, "Position of ", "positionOf") +
                    ReferencePoint.encode(parameters, true);
        }

        @Override
        protected String extractValue(VDJCObject object, ReferencePoint parameters) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; ; i++) {
                sb.append(object.getPartitionedTarget(i).getPartitioning().getPosition(parameters));
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
