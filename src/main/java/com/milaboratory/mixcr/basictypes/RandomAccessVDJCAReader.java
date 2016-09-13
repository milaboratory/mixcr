/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public class RandomAccessVDJCAReader implements AutoCloseable {
    final long[] index;
    final RandomAccessFile raf;
    final VDJCAlignmentsReader innerReader;
    int currentIndex = 0;

    public RandomAccessVDJCAReader(File file, long[] index) {
        this(file, index, VDJCLibraryRegistry.getDefault());
    }

    public RandomAccessVDJCAReader(File file, long[] index, VDJCLibraryRegistry registry) {
        try {
            this.raf = new RandomAccessFile(file, "rw");
            this.index = index;
            this.innerReader = new VDJCAlignmentsReader(raf, registry);
            this.innerReader.init();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public RandomAccessVDJCAReader(String file, long[] index) {
        this(file, index, VDJCLibraryRegistry.getDefault());
    }

    public RandomAccessVDJCAReader(String file, long[] index, VDJCLibraryRegistry registry) {
        try {
            this.raf = new RandomAccessFile(file, "rw");
            this.index = index;
            this.innerReader = new VDJCAlignmentsReader(raf, registry);
            this.innerReader.init();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public VDJCAlignerParameters getParameters() {
        return innerReader.getParameters();
    }

    public List<VDJCGene> getUsedGenes() {
        return innerReader.getUsedGenes();
    }

    /**
     * Returns information about version of MiXCR which produced this file.
     *
     * @return information about version of MiXCR which produced this file
     */
    public String getVersionInfo() {
        return innerReader.getVersionInfo();
    }

    /**
     * Returns magic bytes of this file.
     *
     * @return magic bytes of this file
     */
    public String getMagic() {
        return innerReader.getMagic();
    }

    public synchronized VDJCAlignments get(int ind) {
        try {
            if (ind != currentIndex)
                raf.seek(index[ind]);
            VDJCAlignments alignment = innerReader.take();
            currentIndex = ind + 1;
            alignment.setAlignmentsIndex(ind);
            return alignment;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        raf.close();
    }
}
