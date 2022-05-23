package com.milaboratory.mixcr.tags;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.ShortSequenceSet;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil;
import com.milaboratory.util.FormatUtils;
import com.milaboratory.util.IntArrayList;
import com.milaboratory.util.io.ByteArrayDataOutput;
import com.milaboratory.util.io.IOUtil;
import net.jpountz.lz4.LZ4Compressor;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

public class WhitelistPackerTest {
    public void importWL(String inputFile, String resourceName) throws IOException {
        IntArrayList list = new IntArrayList();
        ShortSequenceSet expectedWhitelist = new ShortSequenceSet();
        System.out.println("Initial file size: " + FormatUtils.bytesToString(new File(inputFile).length()));
        try (
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        new FileInputStream(inputFile)))
        ) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0)
                    continue;
                NucleotideSequence seq = new NucleotideSequence(line);
                if (seq.size() != 16)
                    throw new IllegalArgumentException();

                expectedWhitelist.add(seq);

                int barcodeInt = 0;
                for (int i = 0; i < seq.size(); i++) {
                    byte nuc = seq.codeAt(i);
                    if (nuc > 3)
                        throw new IllegalArgumentException();
                    barcodeInt <<= 2;
                    barcodeInt |= nuc;
                }
                list.add(barcodeInt);
            }
        }

        System.out.println("Data without delta-encoding: " + FormatUtils.bytesToString(expectedWhitelist.size() * 4L));
        list.sort();
        ByteArrayDataOutput bos = new ByteArrayDataOutput();
        PrimitivO o = new PrimitivO(bos);
        int p = 0, c;
        for (int i = 0; i < list.size(); i++) {
            c = list.get(i);
            o.writeVarInt(c - p);
            p = c;
        }
        byte[] buffer = bos.getBuffer();
        System.out.println("Data before compression: " + FormatUtils.bytesToString(buffer.length));
        LZ4Compressor compressor = PrimitivIOBlocksUtil.highLZ4Compressor();
        byte[] compressed = compressor.compress(buffer);
        compressed = Arrays.copyOf(compressed, compressed.length + 8);
        IOUtil.writeIntBE(buffer.length, compressed, compressed.length - 4);
        IOUtil.writeIntBE(list.size(), compressed, compressed.length - 8);
        System.out.println("Data after compression: " + FormatUtils.bytesToString(compressed.length));

        ShortSequenceSet whitelist = WhitelistReader.read10XCompressedWhitelist(compressed);

        try (FileOutputStream fos = new FileOutputStream("src/main/resources/" + resourceName)) {
            fos.write(compressed);
        }

        // System.out.println(whitelist.hashCode());
        // System.out.println(expectedWhitelist.hashCode());

        Assert.assertEquals(expectedWhitelist, whitelist);
    }

    @Ignore
    @Test
    public void test1() throws IOException {
        importWL("/Volumes/Data/Projects/MiLaboratory/data/10x/3M-february-2018.txt", "10x-3M-february-2018.bin");
    }

    @Ignore
    @Test
    public void test2() throws IOException {
        importWL("/Volumes/Data/Projects/MiLaboratory/data/10x/737K-august-2016.txt", "10x-737K-august-2016.bin");
    }
}
