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
package com.milaboratory.mixcr.tags;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.core.sequence.ShortSequenceSet;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil;
import com.milaboratory.util.io.IOUtil;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WhitelistReader {
    public static ShortSequenceSet Whitelist10X2018() {
        try (InputStream in = WhitelistReader.class.getResourceAsStream("/10x-3M-february-2018.bin")) {
            return read10XCompressedWhitelist(IOUtils.toByteArray(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ShortSequenceSet Whitelist10X2016() {
        try (InputStream in = WhitelistReader.class.getResourceAsStream("/10x-737K-august-2016.bin")) {
            return read10XCompressedWhitelist(IOUtils.toByteArray(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static ShortSequenceSet read10XCompressedWhitelist(byte[] data) {
        int uncompressedLen = IOUtil.readIntBE(data, data.length - 4);
        int numElements = IOUtil.readIntBE(data, data.length - 8);
        LZ4FastDecompressor decompressor = PrimitivIOBlocksUtil.defaultLZ4Decompressor();
        byte[] uncompressed = decompressor.decompress(data, 0, uncompressedLen);

        PrimitivI primitivI = new PrimitivI(new ByteArrayInputStream(uncompressed));
        ShortSequenceSet ret = new ShortSequenceSet();
        int c = 0;
        for (int i = 0; i < numElements; i++) {
            c += primitivI.readVarInt();
            SequenceBuilder<NucleotideSequence> seqBuilder = NucleotideSequence.ALPHABET.createBuilder();
            seqBuilder.ensureCapacity(16);
            for (int j = 15; j >= 0; j--) {
                seqBuilder.append((byte) ((c >>> (j << 1)) & 0x3));
            }
            ret.add(seqBuilder.createAndDestroy());
        }
        return ret;
    }
}
