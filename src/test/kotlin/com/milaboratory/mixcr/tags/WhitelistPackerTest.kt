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
package com.milaboratory.mixcr.tags

import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.ShortSequenceSet
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil
import com.milaboratory.util.FormatUtils
import com.milaboratory.util.IntArrayList
import com.milaboratory.util.io.ByteArrayDataOutput
import com.milaboratory.util.io.IOUtil
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class WhitelistPackerTest {
    @Throws(IOException::class)
    fun importWL(inputFile: String?, resourceName: String) {
        val list = IntArrayList()
        val expectedWhitelist = ShortSequenceSet()
        println("Initial file size: " + FormatUtils.bytesToString(File(inputFile).length()))
        BufferedReader(InputStreamReader(FileInputStream(inputFile))).use { r ->
            var line: String
            while (r.readLine().also { line = it } != null) {
                line = line.trim { it <= ' ' }
                if (line.length == 0) continue
                val seq = NucleotideSequence(line)
                require(seq.size() == 16)
                expectedWhitelist.add(seq)
                var barcodeInt = 0
                for (i in 0 until seq.size()) {
                    val nuc: Byte = seq.codeAt(i)
                    require(nuc <= 3)
                    barcodeInt = barcodeInt shl 2
                    barcodeInt = barcodeInt or nuc.toInt()
                }
                list.add(barcodeInt)
            }
        }
        println("Data without delta-encoding: " + FormatUtils.bytesToString(expectedWhitelist.size * 4L))
        list.sort()
        val bos = ByteArrayDataOutput()
        val o = PrimitivO(bos)
        var p = 0
        var c: Int
        for (i in 0 until list.size()) {
            c = list[i]
            o.writeVarInt(c - p)
            p = c
        }
        val buffer = bos.buffer
        println("Data before compression: " + FormatUtils.bytesToString(buffer.size.toLong()))
        val compressor = PrimitivIOBlocksUtil.highLZ4Compressor()
        var compressed = compressor.compress(buffer)
        compressed = Arrays.copyOf(compressed, compressed.size + 8)
        IOUtil.writeIntBE(buffer.size, compressed, compressed.size - 4)
        IOUtil.writeIntBE(list.size(), compressed, compressed.size - 8)
        println("Data after compression: " + FormatUtils.bytesToString(compressed.size.toLong()))
        val whitelist: ShortSequenceSet = WhitelistReader.read10XCompressedWhitelist(compressed)
        FileOutputStream("src/main/resources/$resourceName").use { fos -> fos.write(compressed) }

        // System.out.println(whitelist.hashCode());
        // System.out.println(expectedWhitelist.hashCode());
        Assert.assertEquals(expectedWhitelist, whitelist)
    }

    @Ignore
    @Test
    @Throws(IOException::class)
    fun test1() {
        importWL("/Volumes/Data/Projects/MiLaboratory/data/10x/3M-february-2018.txt", "10x-3M-february-2018.bin")
    }

    @Ignore
    @Test
    @Throws(IOException::class)
    fun test2() {
        importWL("/Volumes/Data/Projects/MiLaboratory/data/10x/737K-august-2016.txt", "10x-737K-august-2016.bin")
    }
}
