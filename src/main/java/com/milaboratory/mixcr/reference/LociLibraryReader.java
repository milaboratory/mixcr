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

import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.util.Bit2Array;

import java.io.*;
import java.util.*;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static com.milaboratory.mixcr.reference.LociLibraryWriter.*;


public class LociLibraryReader {
    final DataInputStream stream;
    final LociLibrary library = new LociLibrary();
    LocusContainer container;
    EnumMap<GeneType, List<Gene>> genes;
    List<Gene> allGenes = null;
    EnumMap<GeneType, List<Allele>> alleles;
    Map<String, Gene> nameToGenes;
    Map<String, Allele> nameToAlleles;

    private LociLibraryReader(InputStream stream) {
        this.stream = new DataInputStream(stream);
    }

    public static LociLibrary read(File file) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            return read(bis);
        }
    }

    public static LociLibrary read(String fileName) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName))) {
            return read(bis);
        }
    }

    public static LociLibrary read(InputStream stream) throws IOException {
        LociLibraryReader reader = new LociLibraryReader(stream);
        reader.checkMagic();
        reader.readToEnd();
        return reader.library;
    }

    private void checkMagic() throws IOException {
        if (stream.readInt() != LociLibraryWriter.MAGIC)
            throw new IOException("Wrong magic bytes.");
    }

    private void readToEnd() throws IOException {
        int b;

        while ((b = stream.read()) != -1) {
            switch ((byte) b) {
                case MAGIC_TYPE:
                    readMagic();
                    break;
                case META_TYPE:
                    readMeta();
                    break;
                case SEQUENCE_PART_ENTRY_TYPE:
                    readSequencePart(false);
                    break;
                case SEQUENCE_PART_ENTRY_TYPE_COMPRESSED:
                    readSequencePart(true);
                    break;
                case LOCUS_BEGIN_TYPE:
                    beginLocus();
                    break;
                case ALLELE_TYPE:
                    readAllele();
                    break;
                case LOCUS_END_TYPE:
                    endLocus();
                    break;
                case SPECIES_NAME_TYPE:
                    readSpeciesName();
                    break;
                default:
                    throw new IOException("Unknown type of record: " + b);
            }
        }

        if (container != null)
            throw new IOException("Premature end of stream.");
    }

    private void readMagic() throws IOException {
        stream.readByte();
        stream.readByte();
        stream.readByte();
    }

    private void beginLocus() throws IOException {
        String locusId = stream.readUTF();
        Locus locus = Locus.fromId(locusId);
        if (locus == null)
            throw new IOException("Unknown locus: " + locusId);
        int taxonId = stream.readInt();
        long lsb = stream.readLong();
        long msb = stream.readLong();
        UUID uuid = new UUID(msb, lsb);

        genes = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.values())
            genes.put(gt, new ArrayList<Gene>());

        alleles = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.values())
            alleles.put(gt, new ArrayList<Allele>());

        container = new LocusContainer(uuid, new SpeciesAndLocus(taxonId, locus), genes, alleles,
                Collections.unmodifiableMap(nameToGenes = new HashMap<String, Gene>()),
                Collections.unmodifiableMap(nameToAlleles = new HashMap<String, Allele>()),
                Collections.unmodifiableList(allGenes = new ArrayList<>()));
    }

    private void readAllele() throws IOException {
        GeneType type = GeneType.get(stream.readByte());
        if (type == null)
            throw new IOException("Unknown gene type.");

        //Reading
        String alleleName = stream.readUTF();
        byte flags = stream.readByte();
        String accession = null;
        int[] referencePoints = null;
        if ((flags & 4) != 0) {
            accession = stream.readUTF();
            referencePoints = new int[GENE_TYPE_INFOS.get(type).size];
            for (int i = 0; i < referencePoints.length; ++i)
                referencePoints[i] = stream.readInt();
        }
        String referenceAllele = null;
        int[] mutations = null;
        if ((flags & 8) != 0) {
            referenceAllele = stream.readUTF();
            int size = stream.readInt();
            mutations = new int[size];
            for (int i = 0; i < size; ++i)
                mutations[i] = stream.readInt();
        }

        //Adding
        String geneName = alleleName.substring(0, alleleName.lastIndexOf('*'));
        Gene gene = nameToGenes.get(geneName);
        if (gene == null) {
            if (referencePoints == null)
                throw new IOException("First gene allele is not reference.");
            List<Gene> gs = genes.get(type);
            gs.add(gene =
                    new Gene(gs.size(), geneName, GeneGroup.get(container.getSpeciesAndLocus().locus, type), container));
            Gene gg = nameToGenes.put(geneName, gene);
            assert gg == null;
            allGenes.add(gene);
        }
        List<Allele> as = alleles.get(type);
        Allele parent = null;
        if (referenceAllele != null) {
            parent = nameToAlleles.get(referenceAllele);
            if (parent == null)
                throw new IOException("No parent allele.");
        }
        Allele allele;
        if ((flags & 1) != 0) {
            //reference allele
            allele = new ReferenceAllele(gene, alleleName, (flags & 2) != 0, accession,
                    GENE_TYPE_INFOS.get(type).create(referencePoints));
        } else {
            //allelic variant
            allele = new AllelicVariant(alleleName,
                    (flags & 2) != 0, parent.getPartitioning().getWrappingGeneFeature(),
                    (ReferenceAllele) parent,
                    new Mutations<NucleotideSequence>(NucleotideSequence.ALPHABET, mutations));
        }

        gene.alleles.add(allele);
        as.add(allele);
        parent = nameToAlleles.put(alleleName, allele);

        //No alleles with the same name
        assert parent == null;
    }

    private void endLocus() {
        for (Map.Entry<GeneType, List<Allele>> e : alleles.entrySet())
            e.setValue(Collections.unmodifiableList(
                    Arrays.asList(
                            e.getValue().toArray(new Allele[e.getValue().size()]))
            ));

        for (Map.Entry<GeneType, List<Gene>> e : genes.entrySet())
            e.setValue(Collections.unmodifiableList(
                    Arrays.asList(
                            e.getValue().toArray(new Gene[e.getValue().size()]))
            ));

        library.registerContainer(container);
        container = null;
        genes = null;
        alleles = null;
        nameToAlleles = null;
        nameToGenes = null;
        allGenes = null;
    }

    private void readSequencePart(boolean compressed) throws IOException {
        String accession = stream.readUTF();
        int from = stream.readInt();
        Bit2Array seqContent;
        if (compressed) {
            int len = stream.readInt();
            byte[] buf = new byte[len];
            int size = stream.read(buf);
            assert size == buf.length;
            Inflater inflater = new Inflater(true);
            try (InflaterInputStream is = new InflaterInputStream(new ByteArrayInputStream(buf), inflater)) {
                seqContent = Bit2Array.readFrom(new DataInputStream(is));
            }
            assert inflater.finished();
            inflater.end();
        } else
            seqContent = Bit2Array.readFrom(stream);
        library.base.put(accession, from, SequencesUtils.convertBit2ArrayToNSequence(seqContent));
    }

    private void readMeta() throws IOException {
        String key = stream.readUTF();
        String value = stream.readUTF();
        if (container == null)
            library.properties.put(key, value);
        else
            container.properties.put(key, value);
    }

    private void readSpeciesName() throws IOException {
        if (container != null)
            throw new IOException("Illegal place for \"common species name\" record.");
        int taxonId = stream.readInt();
        String name = stream.readUTF();
        library.knownSpecies.put(name, taxonId);
    }
}
