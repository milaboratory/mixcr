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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.core.io.util.IOUtil;
import com.milaboratory.util.TempFileManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.milaboratory.core.io.util.IOUtil.*;

public final class AssemblerEventLogger {
    static final int MAX_BUFFER_SIZE = 30_000;
    final AtomicBoolean closed = new AtomicBoolean(false);
    final File file;
    final OutputStream os;
    //todo replace with ArrayDeque
    final ArrayList<AssemblerEvent> eventsBuffer = new ArrayList<>();
    long counter = 0;

    public AssemblerEventLogger() {
        try {
            this.file = TempFileManager.getTempFile();
            this.os = new BufferedOutputStream(new FileOutputStream(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AssemblerEventLogger(File file) {
        this.file = file;
        try {
            this.os = new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException();
        }
    }

    public synchronized void newEvent(AssemblerEvent event) {
        if (event.alignmentsIndex != counter) {
            if (event.alignmentsIndex < counter)
                throw new IllegalArgumentException("Duplicate event detected.");
            eventsBuffer.add(event);
            if (eventsBuffer.size() > MAX_BUFFER_SIZE)
                throw new RuntimeException("Missing event detected.");
            return;
        }

        write(event);
        ++counter;
        if (!eventsBuffer.isEmpty()) {
            Collections.sort(eventsBuffer);
            while (!eventsBuffer.isEmpty()) {
                if (eventsBuffer.get(0).alignmentsIndex != counter)
                    return;
                write(eventsBuffer.remove(0));
                ++counter;
            }
        }
    }

    private void write(AssemblerEvent event) {
        // Just in case (like assert)
        if (event.cloneIndex == -2_147_483_648)
            throw new IllegalArgumentException();

        try {
            // Writing clone index
            writeRawVarint32(os, encodeZigZag32(event.cloneIndex));

        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public Iterable<AssemblerEvent> events() {
        return new Iterable<AssemblerEvent>() {
            @Override
            public Iterator<AssemblerEvent> iterator() {
                try {
                    return new CUtils.OPIterator<>(new EventsPort(new BufferedInputStream(new FileInputStream(file))));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public OutputPortCloseable<AssemblerEvent> createEventsPort() {
        try {
            return new EventsPort(new BufferedInputStream(new FileInputStream(file)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Tells this class that logging is finished, and underlying file can be closet for write.
     */
    public synchronized void end(long check) {
        if (check != counter)
            throw new RuntimeException("Something wrong.");
        end();
    }

    public synchronized void end() {
        //Close only once
        if (closed.compareAndSet(false, true))
            try {
                if (!eventsBuffer.isEmpty())
                    throw new IllegalStateException("Some elements left in buffer.");
                os.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

    /**
     * Deletes underlying file with log information.
     */
    public void close() {
        file.delete();
    }

    private static final class EventsPort implements OutputPortCloseable<AssemblerEvent> {
        volatile boolean closed = false;
        final InputStream is;
        long counter = 0;

        private EventsPort(InputStream is) {
            this.is = is;
        }

        @Override
        public AssemblerEvent take() {
            try {
                if (closed)
                    return null;
                final int cloneIndex;
                synchronized (this) {
                    if (closed)
                        return null;
                    else {
                        // Here -1 can't be returned form the real stream
                        // (only because of EOF)
                        // because IOUtil.decodeZigZag32(-1) == -2_147_483_648
                        cloneIndex = readRawVarint32(is, -1);
                        if (cloneIndex == -1) {
                            closed = true;
                            is.close();
                            return null;
                        }
                    }
                }

                return new AssemblerEvent(counter++, decodeZigZag32(cloneIndex));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void close() {
            if (closed)
                return;
            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
