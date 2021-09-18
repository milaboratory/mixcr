/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
        this(file == null ? new CloseShieldOutputStream(System.out) :
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
