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
package com.milaboratory.mixcr.util;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TempFileManager {
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    static final ConcurrentHashMap<String, File> createdFiles = new ConcurrentHashMap<>();
    static final RandomDataGenerator randomGenerator = new RandomDataGenerator(new Well44497b());

    public static void seed(long seed) {
        synchronized (randomGenerator) {
            randomGenerator.reSeed(seed);
        }
    }

    //static final String tmpdir = getTmpDir();
    //private static String getTmpDir() {
    //    String tmpdir = AccessController.doPrivileged(new GetPropertyAction("java.io.tmpdir"));
    //    if (!tmpdir.endsWith(File.separator))
    //        tmpdir = tmpdir + File.separator;
    //    return tmpdir;
    //}

    public static File getTempFile() {
        try {
            if (initialized.compareAndSet(false, true))
                // Adding delete files shutdown hook on the very firs execution of getTempFile()
                Runtime.getRuntime().addShutdownHook(new Thread(new RemoveAction(), "DeleteTempFiles"));

            File file;
            String name;

            do {
                synchronized (randomGenerator) {
                    name = "mixcr_" + randomGenerator.nextHexString(40);
                }
                file = File.createTempFile(name, null);
            } while (createdFiles.putIfAbsent(name, file) != null);
            if (file.length() != 0)
                throw new RuntimeException();
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class RemoveAction implements Runnable {
        @Override
        public void run() {
            for (File file : createdFiles.values()) {
                if (file.exists())
                    try {
                        file.delete();
                    } catch (RuntimeException e) {
                    }
            }
        }
    }
}
