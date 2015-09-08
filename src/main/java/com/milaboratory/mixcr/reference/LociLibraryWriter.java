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
package com.milaboratory.mixcr.reference;


import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequencesUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class LociLibraryWriter {
    public static final int MAGIC = 0xEB1C0BED;
    public static final byte MAGIC_TYPE = (byte) (MAGIC >>> 24);
    public static final byte SEQUENCE_PART_ENTRY_TYPE = 10;
    public static final byte SEQUENCE_PART_ENTRY_TYPE_COMPRESSED = 14;
    public static final byte LOCUS_BEGIN_TYPE = 11;
    public static final byte LOCUS_END_TYPE = 12;
    public static final byte ALLELE_TYPE = 13;
    public static final byte META_TYPE = 24;
    public static final byte SPECIES_NAME_TYPE = 25;
    private final DataOutputStream stream;

    public LociLibraryWriter(OutputStream outputStream) {
        this.stream = new DataOutputStream(outputStream);
    }

    /**
     * Writes magic bytes that must be at the beginning of the container file.
     *
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeMagic() throws IOException {
        stream.writeInt(MAGIC);
    }

    /**
     * Writes new sequence part entry.
     *
     * @param accession accession number of original sequence
     * @param from      position of first nucleotide of content of this entry in the original sequence (e.g. 0 if this
     *                  entry contains whole sequence)
     * @param sequence  content of this entry
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeSequencePart(String accession, int from, NucleotideSequence sequence, boolean compressed) throws IOException {
        stream.writeByte(compressed ? SEQUENCE_PART_ENTRY_TYPE_COMPRESSED : SEQUENCE_PART_ENTRY_TYPE);
        stream.writeUTF(accession);
        stream.writeInt(from);
        if (compressed) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final Deflater def = new Deflater(9, true);
            DeflaterOutputStream dos = new DeflaterOutputStream(bos, def);
            SequencesUtils.convertNSequenceToBit2Array(sequence).writeTo(new DataOutputStream(dos));
            dos.close();
            def.end();
            stream.writeInt(bos.size());
            stream.write(bos.toByteArray());
        } else
            SequencesUtils.convertNSequenceToBit2Array(sequence).writeTo(stream);
    }

    /**
     * Writes new sequence part entry.
     *
     * @param accession accession number of original sequence
     * @param from      position of first nucleotide of content of this entry in the original sequence (e.g. 0 if this
     *                  entry contains whole sequence)
     * @param sequence  content of this entry
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeSequencePart(String accession, int from, NucleotideSequence sequence) throws IOException {
        writeSequencePart(accession, from, sequence, false);
    }


    /**
     * Writes header of a set of segments (e.g. segments from one locus).
     *
     * @param taxonId NCBI taxon id (see: <a href="http://www.ncbi.nlm.nih.gov/taxonomy">http://www.ncbi.nlm.nih.gov/taxonomy</a>)
     * @param locus   gene
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeBeginOfLocus(int taxonId, Locus locus) throws IOException {
        writeBeginOfLocus(taxonId, locus, UUID.randomUUID());
    }

    /**
     * Writes header of a set of segments (e.g. segments from one locus).
     *
     * @param taxonId NCBI taxon id (see: <a href="http://www.ncbi.nlm.nih.gov/taxonomy">http://www.ncbi.nlm.nih.gov/taxonomy</a>)
     * @param locus   gene
     * @param uuid    uuid
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeBeginOfLocus(int taxonId, Locus locus, UUID uuid) throws IOException {
        stream.writeByte(LOCUS_BEGIN_TYPE);
        stream.writeUTF(locus.getId());
        stream.writeInt(taxonId);
        stream.writeLong(uuid.getLeastSignificantBits());
        stream.writeLong(uuid.getMostSignificantBits());
    }

    /**
     * Writes end of locus.
     *
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeEndOfLocus() throws IOException {
        stream.writeByte(LOCUS_END_TYPE);
    }

    /**
     * Writes meta-information record (key-value pair)
     *
     * @param key   key
     * @param value value
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeMetaInfo(String key, String value) throws IOException {
        stream.writeByte(META_TYPE);
        stream.writeUTF(key);
        stream.writeUTF(value);
    }

    /**
     * Writes common or abbreviation name of some species.
     * <p/>
     * <p>See: <a href="http://www.ncbi.nlm.nih.gov/taxonomy">http://www.ncbi.nlm.nih.gov/taxonomy</a></p>
     *
     * @param speciesName species name
     * @param taxonid     NCBI TaxonID
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeCommonSpeciesName(int taxonid, String speciesName) throws IOException {
        stream.writeByte(SPECIES_NAME_TYPE);
        stream.writeInt(taxonid);
        stream.writeUTF(speciesName);
    }


    /**
     * Writes record with information about particular segment (allele).
     * <p/>
     * <p>There are two main types of such records (depending on value of {@code isReference} option):</p>
     * <p/>
     * <ul>
     * <p/>
     * <li><b>Reference records (like IMGT *01 alleles).</b> Such records must have accession number of source sequence
     * and array of reference points and must not have mutation list and reference allele fields.</li>
     * <p/>
     * <li><b>Allele records (other alleles).</b> Such records must have mutation list and reference allele field.</li>
     * <p/>
     * </ul>
     * <p/>
     * <p>Mutation positions in the mutation list ( {@code mutations} option ) must be converted to positions relative
     * to the first defined reference point of the reference allele.</p>
     *
     * @param type            type of the segment (V, D, J or C)
     * @param alleleName      full name of allele (e.g. TRBV12-2*01)
     * @param isReference     is it a reference allele
     * @param isFunctional    {@code true} if this gene is functional; {@code false} if this gene is pseudogene
     * @param accession       accession number of original (source) sequence
     * @param referencePoints array of reference points
     * @param reference       allele name of the reference allele
     * @param mutations       array of mutations
     * @throws java.io.IOException if an I/O error occurs.
     */
    public void writeAllele(GeneType type, String alleleName, boolean isReference, boolean isFunctional,
                            String accession, int[] referencePoints,
                            String reference, int[] mutations) throws IOException {
        //Validation
        if ((accession == null) != (referencePoints == null)
                || (reference == null) != (mutations == null))
            throw new IllegalArgumentException();
        if (isReference && (mutations != null || accession == null))
            throw new IllegalArgumentException();
        if (mutations == null && accession == null)
            throw new IllegalArgumentException();

        stream.writeByte(ALLELE_TYPE);
        stream.writeByte(type.id());
        stream.writeUTF(alleleName);
        stream.writeByte((isReference ? 1 : 0) |
                (isFunctional ? 2 : 0) |
                (accession != null ? 4 : 0) |
                (reference != null ? 8 : 0));

        if (accession != null) {
            if (gtis.get(type).size != referencePoints.length)
                throw new IllegalArgumentException("Wrong number of reference points.");

            stream.writeUTF(accession);
            for (int i = 0; i < referencePoints.length; ++i)
                stream.writeInt(referencePoints[i]);
        }

        if (mutations != null) {
            stream.writeUTF(reference);
            stream.writeInt(mutations.length);
            for (int i = 0; i < mutations.length; ++i)
                stream.writeInt(mutations[i]);
        }
    }

    /**
     * Factory class: creates reference points object from raw reference points array for a particular type.
     */
    public static abstract class GeneTypeInfo {
        public final int size;

        protected GeneTypeInfo(int size) {
            this.size = size;
        }

        /**
         * Creates reference points object from raw reference points array for a particular type.
         *
         * @param refsFromFile raw array of reference point
         * @return reference points objects
         */
        public abstract ReferencePoints create(int[] refsFromFile);
    }

    public static class GeneralGeneTypeInfo extends GeneTypeInfo {
        final int indexOfFirstPoint;

        protected GeneralGeneTypeInfo(int size, int indexOfFirstPoint) {
            super(size);
            this.indexOfFirstPoint = indexOfFirstPoint;
        }

        @Override
        public ReferencePoints create(int[] refsFromFile) {
            // Checking length of the array
            if (refsFromFile.length != size)
                throw new IllegalArgumentException("Wrong number of reference points.");
            // Creating reference points object
            return new ReferencePoints(indexOfFirstPoint, refsFromFile);
        }
    }

    // Class to fix for FR4End issue in J genes.
    public static class JGeneTypeInfo extends GeneTypeInfo {
        public JGeneTypeInfo() {
            super(3);
        }

        @Override
        public ReferencePoints create(int[] refsFromFile) {
            // Checking length of the array
            if (refsFromFile.length != 3)
                throw new IllegalArgumentException("Wrong number of reference points.");

            // If one of boundaries of FR4 is not defined, don't perform correction.
            if (refsFromFile[2] == -1 || refsFromFile[1] == -1)
                return new ReferencePoints(13, refsFromFile);

            // Performing correction
            int[] newRefs = refsFromFile.clone();
            int fr4Length = newRefs[2] - newRefs[1];

            // Checking for strand
            // Main algorithm move two amino acids back from the end of original J gene and correct for reading frame:
            // to make fr4Length dividable by 3.
            if (fr4Length > 0)
                newRefs[2] = newRefs[1] + ((fr4Length / 3 - 1) * 3);
            else
                newRefs[2] = newRefs[1] - (((-fr4Length) / 3 - 1) * 3);

            return new ReferencePoints(13, newRefs);
        }
    }

    public static final EnumMap<GeneType, GeneTypeInfo> gtis = new EnumMap<>(GeneType.class);

    static {
        gtis.put(GeneType.Variable, new GeneralGeneTypeInfo(11, 0));
        gtis.put(GeneType.Diversity, new GeneralGeneTypeInfo(2, 11));
//        gtis.put(GeneType.Joining, new JGeneTypeInfo());
        gtis.put(GeneType.Joining, new GeneralGeneTypeInfo(3, 13));
        gtis.put(GeneType.Constant, new GeneralGeneTypeInfo(3, 16));
    }
}