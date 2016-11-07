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

import cc.redberry.pipe.InputPort;
import org.apache.commons.io.output.CloseShieldOutputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class InfoWriter<T> implements InputPort<T>, AutoCloseable {
    final ArrayList<FieldExtractor<? super T>> fieldExtractors = new ArrayList<>();
    final OutputStream outputStream;
    boolean initialized;

    public InfoWriter(String file) throws FileNotFoundException {
        this(".".equals(file) ? new CloseShieldOutputStream(System.out) :
                new BufferedOutputStream(new FileOutputStream(new File(file)), 65536));
    }

    public void attachInfoProvider(FieldExtractor<? super T> provider) {
        fieldExtractors.add(provider);
    }

    public void attachInfoProviders(List<FieldExtractor<? super T>> providers) {
        fieldExtractors.addAll(providers);
    }

    public InfoWriter(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void ensureHeader() {
        if (!initialized) {
            try {
                for (int i = 0; i < fieldExtractors.size(); ++i) {
                    outputStream.write(fieldExtractors.get(i).getHeader().getBytes());
                    if (i == fieldExtractors.size() - 1)
                        break;
                    outputStream.write('\t');
                }
                outputStream.write('\n');
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            initialized = true;
        }
    }

    @Override
    public void put(T t) {
        ensureHeader();
        try {
            for (int i = 0; i < fieldExtractors.size(); ++i) {
                outputStream.write(fieldExtractors.get(i).extractValue(t).getBytes());
                if (i == fieldExtractors.size() - 1)
                    break;
                outputStream.write('\t');
            }
            outputStream.write('\n');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
        for (FieldExtractor<? super T> fe : fieldExtractors)
            if (fe instanceof Closeable)
                ((Closeable) fe).close();
    }
}
