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
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.ActionAssemble;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.ReferencePoint;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.MappingType.Dropped;

public final class FieldExtractors {
    private static final String NULL = "";
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#.#");

    static Field[] descriptors = null;

    public synchronized static Field[] getFields() {
        if (descriptors == null) {
            List<Field> desctiptorsList = new ArrayList<>();

            // Number of targets
            desctiptorsList.add(new PL_O("-targets", "Export number of targets", "Number of targets", "numberOfTargets") {
                @Override
                protected String extract(VDJCObject object) {
                    return Integer.toString(object.numberOfTargets());
                }
            });

            // Best hits
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Hit",
                        "Export best " + l + " hit", "Best " + l + " hit", "best" + l + "Hit") {
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
                        "Export best score for best " + l + " hit", "Best " + l + " hit score", "best" + l + "HitScore") {
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
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "HitsWithScore",
                        "Export all " + l + " hits with score", "All " + l + " hits", "all" + l + "HitsWithScore") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
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

            // All hits without score
            for (final GeneType type : GeneType.values()) {
                char l = type.getLetter();
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Hits",
                        "Export all " + l + " hits", "All " + l + " Hits", "all" + l + "Hits") {
                    @Override
                    protected String extract(VDJCObject object) {
                        VDJCHit[] hits = object.getHits(type);
                        if (hits.length == 0)
                            return "";
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; ; i++) {
                            sb.append(hits[i].getAllele().getName());
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
                desctiptorsList.add(new PL_O("-" + Character.toLowerCase(l) + "Alignments",
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

            desctiptorsList.add(new FeatureExtractorDescriptor("-nFeature", "Export nucleotide sequence of specified gene feature", "N. Seq.", "nSeq") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return seq.getSequence().toString();
                }
            });

            desctiptorsList.add(new FeatureExtractorDescriptor("-qFeature", "Export quality string of specified gene feature", "Qual.", "qual") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return seq.getQuality().toString();
                }
            });

            desctiptorsList.add(new FeatureExtractorDescriptor("-aaFeature", "Export amino acid sequence of specified gene feature", "AA. Seq.", "aaSeq") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return AminoAcidSequence.translate(null, true, seq.getSequence()).toString();
                }
            });

            desctiptorsList.add(new FeatureExtractorDescriptor("-minFeatureQuality", "Export minimal quality of specified gene feature", "Min. qual.", "minQual") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return "" + seq.getQuality().minValue();
                }
            });

            desctiptorsList.add(new FeatureExtractorDescriptor("-avrgFeatureQuality", "Export average quality of specified gene feature", "Mean. qual.", "meanQual") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return "" + seq.getQuality().meanValue();
                }
            });

            desctiptorsList.add(new FeatureExtractorDescriptor("-lengthOf", "Exports length of specified gene feature.", "Length of ", "lengthOf") {
                @Override
                public String convert(NSequenceWithQuality seq) {
                    return "" + seq.size();
                }
            });

            desctiptorsList.add(new ExtractReferencePointPosition());

            desctiptorsList.add(new ExtractDefaultReferencePointsPositions());

            desctiptorsList.add(new PL_A("-readId", "Export number of read corresponding to alignment", "Read id", "readId") {
                @Override
                protected String extract(VDJCAlignments object) {
                    return "" + object.getReadId();
                }
            });

            desctiptorsList.add(new ExtractSequence(VDJCAlignments.class, "-sequence",
                    "Export aligned sequence (initial read), or 2 sequences in case of paired-end reads",
                    "Read(s) sequence", "readSequence"));

            desctiptorsList.add(new ExtractSequenceQuality(VDJCAlignments.class, "-quality",
                    "Export initial read quality, or 2 qualities in case of paired-end reads",
                    "Read(s) sequence qualities", "readQuality"));

            desctiptorsList.add(new PL_C("-cloneId", "Unique clone identifier", "Clone ID", "cloneId") {
                @Override
                protected String extract(Clone object) {
                    return "" + object.getId();
                }
            });

            desctiptorsList.add(new PL_C("-count", "Export clone count", "Clone count", "cloneCount") {
                @Override
                protected String extract(Clone object) {
                    return "" + object.getCount();
                }
            });

            desctiptorsList.add(new PL_C("-fraction", "Export clone fraction", "Clone fraction", "cloneFraction") {
                @Override
                protected String extract(Clone object) {
                    return "" + object.getFraction();
                }
            });

            desctiptorsList.add(new ExtractSequence(Clone.class, "-sequence",
                    "Export aligned sequence (initial read), or 2 sequences in case of paired-end reads",
                    "Clonal sequence(s)", "clonalSequence"));

            desctiptorsList.add(new ExtractSequenceQuality(Clone.class, "-quality",
                    "Export initial read quality, or 2 qualities in case of paired-end reads",
                    "Clonal sequence quality(s)", "clonalSequenceQuality"));

            desctiptorsList.add(new PL_A("-descrR1", "Export description line from initial .fasta or .fastq file " +
                    "of the first read (only available if --save-description was used in align command)", "Description R1", "descrR1") {
                @Override
                protected String extract(VDJCAlignments object) {
                    String[] ds = object.getDescriptions();
                    if (ds == null || ds.length == 0)
                        throw new IllegalArgumentException("Error for option \'-descrR1\':\n" +
                                "No description available for read: either re-run align action with --save-description option " +
                                "or don't use \'-descrR1\' in exportAlignments");
                    return ds[0];
                }
            });

            desctiptorsList.add(new PL_A("-descrR2", "Export description line from initial .fasta or .fastq file " +
                    "of the second read (only available if --save-description was used in align command)", "Description R2", "descrR2") {
                @Override
                protected String extract(VDJCAlignments object) {
                    String[] ds = object.getDescriptions();
                    if (ds == null || ds.length < 2)
                        throw new IllegalArgumentException("Error for option \'-descrR2\':\n" +
                                "No description available for second read: either re-run align action with --save-description option " +
                                "or don't use \'-descrR2\' in exportAlignments");
                    return ds[1];
                }
            });

            desctiptorsList.add(alignmentsToClone("-cloneId", "To which clone alignment was attached.", false));
            desctiptorsList.add(alignmentsToClone("-mapping", "To which clone alignment was attached with additional info on mapping type.", true));
            desctiptorsList.add(new AbstractField<Clone>(Clone.class, "-mapping", "Read IDs aggregated by clone.") {
                @Override
                public FieldExtractor<Clone> create(OutputMode outputMode, String[] args) {
                    return new CloneToReadsExtractor(outputMode, args[0]);
                }
            });
            descriptors = desctiptorsList.toArray(new Field[desctiptorsList.size()]);
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
                description[0].add(field.getCommand());
                description[1].add(field.getDescription());
            }

        return description;
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

    private static abstract class FeatureExtractorDescriptor extends WP_O<GeneFeature> {
        final String hPrefix, sPrefix;

        protected FeatureExtractorDescriptor(String command, String description, String hPrefix, String sPrefix) {
            super(command, description);
            this.hPrefix = hPrefix;
            this.sPrefix = sPrefix;
        }

        @Override
        protected GeneFeature getParameters(String[] string) {
            if (string.length != 1)
                throw new RuntimeException("Wrong number of parameters for " + getCommand());
            return GeneFeature.parse(string[0]);
        }

        @Override
        protected String getHeader(OutputMode outputMode, GeneFeature parameters) {
            return choose(outputMode, hPrefix + " ", sPrefix) + GeneFeature.encode(parameters);
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
    }

    private static class ExtractDefaultReferencePointsPositions extends PL_O {
        public ExtractDefaultReferencePointsPositions() {
            super("-defaultAnchorPoints", "Outputs a list of default reference points (like CDR2Begin, FR4End, etc. " +
                    "see documentation for the full list and formatting)", "Ref. points", "refPoints");
        }

        @Override
        protected String extract(VDJCObject object) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; ; i++) {
                SequencePartitioning partitioning = object.getPartitionedTarget(i).getPartitioning();
                for (int j = 0; ; j++) {
                    int referencePointPosition = partitioning.getPosition(ReferencePoint.DefaultReferencePoints[j]);
                    if (referencePointPosition >= 0)
                        sb.append(referencePointPosition);
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

    }


    private static AbstractField<VDJCAlignments> alignmentsToClone(
            final String command, final String description, final boolean printMapping) {
        return new AbstractField<VDJCAlignments>(VDJCAlignments.class, command, description) {
            @Override
            public FieldExtractor<VDJCAlignments> create(OutputMode outputMode, String[] args) {
                return new AlignmentToCloneExtractor(outputMode, args[0], printMapping);
            }
        };
    }

    private static final class AlignmentToCloneExtractor
            implements FieldExtractor<VDJCAlignments>, Closeable {
        private final OutputMode outputMode;
        private final DB db;
        private final NavigableSet<ReadToCloneMapping> byAls;
        private final boolean printMapping;
        private final Iterator<ReadToCloneMapping> mappingIterator;
        private ReadToCloneMapping currentMapping = null;

        public AlignmentToCloneExtractor(OutputMode outputMode, String file, boolean printMapping) {
            this.outputMode = outputMode;
            this.printMapping = printMapping;
            this.db = DBMaker.newFileDB(new File(file))
                    .transactionDisable()
                    .make();
            this.byAls = db.getTreeSet(ActionAssemble.MAPDB_SORTED_BY_ALIGNMENT);
            this.mappingIterator = byAls.iterator();
        }

        @Override
        public String getHeader() {
            if (printMapping)
                return choose(outputMode, "Clone mapping", "cloneMapping");
            else
                return choose(outputMode, "Clone Id", "cloneId");
        }

        @Override
        public String extractValue(VDJCAlignments object) {
            if (currentMapping == null && mappingIterator.hasNext())
                currentMapping = mappingIterator.next();
            if (currentMapping == null)
                return NULL;

            while (currentMapping.getAlignmentsId() < object.getAlignmentsIndex() && mappingIterator.hasNext())
                currentMapping = mappingIterator.next();
            if (currentMapping.getAlignmentsId() != object.getAlignmentsIndex())
                return printMapping ? Dropped.toString().toLowerCase() : NULL;

            int cloneIndex = currentMapping.getCloneIndex();
            ReadToCloneMapping.MappingType mt = currentMapping.getMappingType();
            if (mt == Dropped)
                return printMapping ? mt.toString().toLowerCase() : NULL;
            return printMapping ? Integer.toString(cloneIndex) + ":" + mt.toString().toLowerCase() : Integer.toString(cloneIndex);
        }

        @Override
        public void close() throws IOException {
            db.close();
        }
    }

    private static final class CloneToReadsExtractor
            implements FieldExtractor<Clone>, Closeable {
        private final OutputMode outputMode;
        private final DB db;
        private final NavigableSet<ReadToCloneMapping> byClones;
        private final Iterator<ReadToCloneMapping> mappingIterator;
        private ReadToCloneMapping currentMapping;

        public CloneToReadsExtractor(OutputMode outputMode, String file) {
            this.outputMode = outputMode;
            this.db = DBMaker.newFileDB(new File(file))
                    .transactionDisable()
                    .make();
            this.byClones = db.getTreeSet(ActionAssemble.MAPDB_SORTED_BY_CLONE);
            this.mappingIterator = byClones.iterator();
        }

        @Override
        public String getHeader() {
            return choose(outputMode, "Reads", "reads");
        }

        @Override
        public String extractValue(Clone object) {
            if (currentMapping == null && mappingIterator.hasNext())
                currentMapping = mappingIterator.next();
            if (currentMapping == null)
                return NULL;

            while (currentMapping.getCloneIndex() < object.getId() && mappingIterator.hasNext())
                currentMapping = mappingIterator.next();

            long count = 0;
            StringBuilder sb = new StringBuilder();
            while (currentMapping.getCloneIndex() == object.getId()) {
                ++count;
                assert currentMapping.getCloneIndex() == currentMapping.getCloneIndex();
                sb.append(currentMapping.getReadId()).append(",");
                if (!mappingIterator.hasNext())
                    break;
                currentMapping = mappingIterator.next();
            }
            //count == object.getCount() only if addReadsCountOnClustering: true
            assert count >= object.getCount() : "Actual count: " + object.getCount() + ", in mapping: " + count;
            if (sb.length() != 0)
                sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }

        @Override
        public void close() throws IOException {
            db.close();
        }
    }

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
}
