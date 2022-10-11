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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.io.CompressionType;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.trees.SHMTreesReader;
import com.milaboratory.primitivio.HasPrimitivIOState;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import io.repseq.core.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class IOUtil {
    public static final int BEGIN_MAGIC_LENGTH = 14;
    public static final int BEGIN_MAGIC_LENGTH_SHORT = 10;

    public static final String MAGIC_VDJC = "MiXCR.VDJC";
    public static final String MAGIC_CLNS = "MiXCR.CLNS";
    public static final String MAGIC_CLNA = "MiXCR.CLNA";
    public static final String MAGIC_SHMT = "MiXCR.SHMT";

    public static final String END_MAGIC = "#MiXCR.File.End#";
    private static final byte[] END_MAGIC_BYTES = END_MAGIC.getBytes(StandardCharsets.US_ASCII);
    public static final int END_MAGIC_LENGTH = END_MAGIC_BYTES.length;

    public static byte[] getEndMagicBytes() {
        return END_MAGIC_BYTES.clone();
    }

    /**
     * Writes minimal required header information to PrimitivO state and executes minimal required state initialization
     * procedure for compact serialization of {@link VDJCAlignments} objects (so that all the sequences and genes will
     * be serialized as references).
     *
     * Use {@link IOUtil#stdVDJCPrimitivIStateInit(PrimitivI, HasFeatureToAlign, VDJCLibraryRegistry)} as this method
     * counterpart.
     */
    public static void stdVDJCPrimitivOStateInit(PrimitivO o, List<VDJCGene> genes,
                                                 HasFeatureToAlign featuresToAlign) {
        // Registering links to features to align
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature feature = featuresToAlign.getFeatureToAlign(gt);
            o.writeObject(feature);
            if (feature != null)
                o.putKnownObject(feature);
        }

        writeAndRegisterGeneReferences(o, genes, featuresToAlign);
    }

    public static void writeAndRegisterGeneReferences(PrimitivO output, List<VDJCGene> genes,
                                                      HasFeatureToAlign featuresToAlign) {
        // Writing gene ids
        output.writeInt(genes.size());
        for (VDJCGene gene : genes)
            output.writeObject(gene.getId());

        registerGeneReferences(output, genes, featuresToAlign);
    }

    public static void registerGeneReferences(HasPrimitivIOState ioState, Collection<VDJCGene> genes,
                                              HasFeatureToAlign featuresToAlign) {
        // Putting genes references and feature sequences to be serialized/deserialized as references
        for (VDJCGene gene : genes) {
            // Each gene is a singleton
            ioState.putKnownReference(gene);
            // Also put sequences of certain gene features of genes as known references if required
            GeneFeature featureToAlign = featuresToAlign.getFeatureToAlign(gene.getGeneType());
            if (featureToAlign == null)
                continue;
            NucleotideSequence featureSequence = gene.getFeature(featureToAlign);
            if (featureSequence == null)
                continue;
            // Relies on the fact that sequences of gene features are cached,
            // the same instance will be used everywhere (including alignments)
            ioState.putKnownReference(gene.getFeature(featuresToAlign.getFeatureToAlign(gene.getGeneType())));
        }
    }

    /**
     * See {@link IOUtil#stdVDJCPrimitivOStateInit(PrimitivO, List, HasFeatureToAlign)}.
     */
    public static List<VDJCGene> stdVDJCPrimitivIStateInit(PrimitivI i, HasFeatureToAlign featuresToAlign,
                                                           VDJCLibraryRegistry registry) {
        // Registering links to features to align
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature featureParams = featuresToAlign.getFeatureToAlign(gt);
            GeneFeature featureDeserialized = i.readObject(GeneFeature.class);
            if (!Objects.equals(featureDeserialized, featureParams))
                throw new RuntimeException("Wrong format.");

            if (featureParams != null)
                i.putKnownObject(featureParams);
        }

        return readAndRegisterGeneReferences(i, registry, featuresToAlign);
    }

    public static List<VDJCGene> readAndRegisterGeneReferences(PrimitivI input, VDJCLibraryRegistry registry,
                                                               HasFeatureToAlign featuresToAlign) {
        List<VDJCGene> genes = readGeneReferences(input, registry);
        registerGeneReferences(input, genes, featuresToAlign);
        return genes;
    }

    public static List<VDJCGene> readGeneReferences(PrimitivI input, VDJCLibraryRegistry registry) {
        // Reading gene ids
        int count = input.readInt();
        List<VDJCGene> genes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            VDJCGeneId id = input.readObject(VDJCGeneId.class);
            VDJCGene gene = registry.getGene(id);
            if (gene == null)
                throw new RuntimeException("Gene not found: " + id);
            genes.add(gene);
        }

        return genes;
    }

    public static InputStream createIS(String file) throws IOException {
        return createIS(CompressionType.detectCompressionType(file), new FileInputStream(file));
    }

    public static InputStream createIS(File file) throws IOException {
        return createIS(CompressionType.detectCompressionType(file), new FileInputStream(file));
    }

    public static InputStream createIS(CompressionType ct, InputStream is) throws IOException {
        if (ct == CompressionType.None)
            return new BufferedInputStream(is, 65536);
        else return ct.createInputStream(is, 65536);
    }

    public static OutputStream createOS(String file) throws IOException {
        return createOS(CompressionType.detectCompressionType(file), new FileOutputStream(file));
    }

    public static OutputStream createOS(File file) throws IOException {
        return createOS(CompressionType.detectCompressionType(file), new FileOutputStream(file));
    }

    public static OutputStream createOS(CompressionType ct, OutputStream os) throws IOException {
        if (ct == CompressionType.None)
            return new BufferedOutputStream(os, 65536);
        else return ct.createOutputStream(os, 65536);
    }

    public enum MiXCRFileType {
        CLNA, CLNS, VDJCA, SHMT
    }

    @NotNull
    public static MiXCRFileType extractFileType(Path path) {
        try {
            if (!Files.isRegularFile(path))
                throw new IllegalArgumentException("Not a regular file: " + path);

            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                if (channel.size() < BEGIN_MAGIC_LENGTH + END_MAGIC_LENGTH)
                    throw new IllegalArgumentException("Unknown file type: " + path);

                byte[] beginMagic = new byte[BEGIN_MAGIC_LENGTH];
                channel.read(ByteBuffer.wrap(beginMagic));
                String magicShort = new String(beginMagic, 0, BEGIN_MAGIC_LENGTH_SHORT,
                        StandardCharsets.US_ASCII);

                byte[] endMagic = new byte[END_MAGIC_LENGTH];
                channel.read(ByteBuffer.wrap(endMagic), channel.size() - END_MAGIC_LENGTH);
                switch (magicShort) {
                    case MAGIC_VDJC:
                        return MiXCRFileType.VDJCA;
                    case MAGIC_CLNA:
                        return MiXCRFileType.CLNA;
                    case MAGIC_CLNS:
                        return MiXCRFileType.CLNS;
                    case MAGIC_SHMT:
                        return MiXCRFileType.SHMT;
                    default:
                        throw new IllegalArgumentException("Unknown file type: " + path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MiXCRFooter extractFooter(Path file) {
        switch (extractFileType(file)) {
            case VDJCA:
                try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(file)) {
                    return reader.getFooter();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case CLNA:
                try (ClnAReader reader = new ClnAReader(file, VDJCLibraryRegistry.getDefault(), 1)) {
                    return reader.getFooter();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case CLNS:
                try (ClnsReader reader = new ClnsReader(file, VDJCLibraryRegistry.getDefault(), 1)) {
                    return reader.getFooter();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case SHMT:
                try (SHMTreesReader reader = new SHMTreesReader(file, VDJCLibraryRegistry.getDefault())) {
                    return reader.getFooter();
                }
            default:
                throw new RuntimeException();
        }
    }

    public static MiXCRHeader extractHeader(Path file) {
        switch (extractFileType(file)) {
            case VDJCA:
                try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(file)) {
                    return reader.getHeader();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case CLNA:
                try (ClnAReader reader = new ClnAReader(file, VDJCLibraryRegistry.getDefault(), 1)) {
                    return reader.getHeader();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case CLNS:
                try (ClnsReader reader = new ClnsReader(file, VDJCLibraryRegistry.getDefault(), 1)) {
                    return reader.getHeader();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case SHMT:
                try (SHMTreesReader reader = new SHMTreesReader(file, VDJCLibraryRegistry.getDefault())) {
                    return reader.getHeader();
                }
            default:
                throw new RuntimeException();
        }
    }
}
