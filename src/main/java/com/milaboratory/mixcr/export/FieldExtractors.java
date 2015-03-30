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

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FieldExtractors {
    private static final String NULL = "";
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#.#");

    static Field[] descriptors = null;

    public synchronized static Field[] getFields() {
        if (descriptors == null) {
            List<Field> desctiptorsList = new ArrayList<>();

            // Number of targets
            desctiptorsList.add(new PL_O("-targets", "Export number of targets", "Number of targets") {
                @Override
                protected String extract(VDJCObject object) {
                    return Integer.toString(object.numberOfTargets());
                }
            });

            // Best hits
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Hit",
                        "Export best " + l + " hit", "Best " + l + " hit") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit bestHit = object.getBestHit(type);
                        if (bestHit == null)
                            return NULL;
                        return bestHit.getAllele().getName();
                    }
                });
            }

            // Best hits
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "HitScore",
                        "Export best score for best " + l + " hit", "Best " + l + " hit score") {
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
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Hits",
                        "Export all " + l + " hits", "All " + l + " hits") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits == null)
                            return NULL;
                        if (hits.length == 0)
                            return "";
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; ; i++) {
                            sb.append(hits[i].getAllele().getName())
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

            // Best alignment
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Alignment",
                        "Export best " + l + " alignment", "Best " + l + " alignment") {
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
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Alignments",
                        "Export all " + l + " alignments", "All " + l + " alignment") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits == null)
                            return NULL;
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

                desctiptorsList.add(new FeatureExtractorDescriptor("-nFeature", "Export nucleotide sequence of specified gene feature", "N. Seq.") {
                    @Override
                    public String convert(NSequenceWithQuality seq) {
                        return seq.getSequence().toString();
                    }
                });

                desctiptorsList.add(new FeatureExtractorDescriptor("-qFeature", "Export quality string of specified gene feature", "Qual.") {
                    @Override
                    public String convert(NSequenceWithQuality seq) {
                        return seq.getQuality().toString();
                    }
                });

                desctiptorsList.add(new FeatureExtractorDescriptor("-aaFeature", "Export amino acid sequence of specified gene feature", "AA. seq.") {
                    @Override
                    public String convert(NSequenceWithQuality seq) {
                        return AminoAcidSequence.translate(null, true, seq.getSequence()).toString();
                    }
                });

                desctiptorsList.add(new FeatureExtractorDescriptor("-minFeatureQuality", "Export minimal quality of specified gene feature", "Min. qual.") {
                    @Override
                    public String convert(NSequenceWithQuality seq) {
                        return "" + seq.getQuality().minValue();
                    }
                });

                desctiptorsList.add(new FeatureExtractorDescriptor("-avrgFeatureQuality", "Export average quality of specified gene feature", "Avrg. qual.") {
                    @Override
                    public String convert(NSequenceWithQuality seq) {
                        return "" + seq.getQuality().meanValue();
                    }
                });

                desctiptorsList.add(new PL_A("-readId", "Export number of read corresponding to alignment", "Read id") {
                    @Override
                    protected String extract(VDJCAlignments object) {
                        return "" + object.getReadId();
                    }
                });

                desctiptorsList.add(new ExtractSequence(VDJCAlignments.class, "-sequence",
                        "Export aligned sequence (initial read), or 2 sequences in case of paired-end reads",
                        "Read(s) sequence"));

                desctiptorsList.add(new ExtractSequenceQaulity(VDJCAlignments.class, "-quality",
                        "Export initial read quality, or 2 qualities in case of paired-end reads",
                        "Read(s) sequence qualities"));

                desctiptorsList.add(new PL_C("-count", "Export clone count", "Clone count") {
                    @Override
                    protected String extract(Clone object) {
                        return "" + object.getCount();
                    }
                });

                desctiptorsList.add(new PL_C("-fraction", "Export clone fraction", "Clone fraction") {
                    @Override
                    protected String extract(Clone object) {
                        return "" + object.getFraction();
                    }
                });

                desctiptorsList.add(new ExtractSequence(Clone.class, "-sequence",
                        "Export aligned sequence (initial read), or 2 sequences in case of paired-end reads",
                        "Clonal sequence(s)"));

                desctiptorsList.add(new ExtractSequenceQaulity(Clone.class, "-quality",
                        "Export initial read quality, or 2 qualities in case of paired-end reads",
                        "Clonal sequence quality(s)"));

            }

            descriptors = desctiptorsList.toArray(new Field[desctiptorsList.size()]);
        }

        return descriptors;
    }

    public static FieldExtractor parse(Class clazz, String[] args) {
        for (Field field : getFields())
            if (field.canExtractFrom(clazz) && args[0].equalsIgnoreCase(field.getCommand()))
                return field.create(Arrays.copyOfRange(args, 1, args.length));
        return null;
    }

    public static ArrayList<String>[] getDescription(Class clazz) {
        ArrayList<String>[] description = new ArrayList[]{new ArrayList(), new ArrayList()};
        for (Field field : getFields())
            if (field.canExtractFrom(clazz)) {
                description[0].add(field.getCommand());
                description[1].add(field.getDescription());
            }

        return description;
    }

    /* Some typedefs */
    static abstract class PL_O extends FieldParameterless<VDJCObject> {
        PL_O(String command, String description, String header) {
            super(VDJCObject.class, command, description, header);
        }
    }

    static abstract class PL_A extends FieldParameterless<VDJCAlignments> {
        PL_A(String command, String description, String header) {
            super(VDJCAlignments.class, command, description, header);
        }
    }

    static abstract class PL_C extends FieldParameterless<Clone> {
        PL_C(String command, String description, String header) {
            super(Clone.class, command, description, header);
        }
    }

    static abstract class WP_O<P> extends FieldWithParameters<VDJCObject, P> {
        protected WP_O(String command, String description) {
            super(VDJCObject.class, command, description);
        }
    }

    private static abstract class FeatureExtractorDescriptor extends WP_O<GeneFeature> {
        final String prefix;

        protected FeatureExtractorDescriptor(String command, String description, String prefix) {
            super(command, description);
            this.prefix = prefix;
        }

        @Override
        protected GeneFeature getParameters(String[] string) {
            if (string.length != 1)
                throw new RuntimeException("Wrong number of parameters for " + getCommand());
            return GeneFeature.parse(string);
        }

        @Override
        protected String getHeader(GeneFeature parameters) {
            return prefix + " " + GeneFeature.encode(parameters);
        }

        @Override
        protected String extractValue(VDJCObject object, GeneFeature parameters) {
            NSequenceWithQuality feature = object.getFeature(parameters);
            if (feature == null)
                return NULL;
            return convert(feature);
        }

        public abstract String convert(NSequenceWithQuality seq);
    }

    private static class ExtractSequence extends FieldParameterless<VDJCObject> {
        private ExtractSequence(Class targetType, String command, String description, String header) {
            super(targetType, command, description, header);
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

    private static class ExtractSequenceQaulity extends FieldParameterless<VDJCObject> {
        private ExtractSequenceQaulity(Class targetType, String command, String description, String header) {
            super(targetType, command, description, header);
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
}
