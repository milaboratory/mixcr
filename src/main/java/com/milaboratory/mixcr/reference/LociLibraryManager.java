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

import com.milaboratory.mixcr.cli.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public final class LociLibraryManager implements AlleleResolver {
    private static volatile LociLibraryManager defualt;

    private final HashMap<AlleleId, Allele> allAlleles = new HashMap<>();
    private final HashMap<String, LociLibrary> libraries = new HashMap<>();

    public void register(String name, LociLibrary library) {
        for (Allele allele : library.getAllAlleles())
            allAlleles.put(allele.getId(), allele);
        libraries.put(name, library);
    }

    public Allele getAllele(AlleleId id) {
        return allAlleles.get(id);
    }

    public LociLibrary getLibrary(String name) {
        return libraries.get(name);
    }

    public static LociLibraryManager getDefault() {
        if (defualt == null)
            synchronized (LociLibraryManager.class) {
                if (defualt == null) {
                    try {
                        defualt = new LociLibraryManager();
                        try (InputStream sample = LociLibraryManager.class.getClassLoader()
                                .getResourceAsStream("reference/mi.ll")) {
                            defualt.register("mi", LociLibraryReader.read(sample, true));
                        }
                        File settings = Util.getLocalSettingsDir().toFile();
                        if (settings.exists())
                            for (File file : settings.listFiles()) {
                                if (file.isFile() && file.getName().endsWith(".ll")) {
                                    defualt.register(file.getName().substring(0, file.getName().length() - 3),
                                            LociLibraryReader.read(file, false));
                                }
                            }
                    } catch (IOException e) {
                        throw new RuntimeException();
                    }
                }
            }
        return defualt;
    }

    static {

    }
}
