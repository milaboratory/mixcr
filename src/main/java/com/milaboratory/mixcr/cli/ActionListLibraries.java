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
package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.AllowNoArguments;
import io.repseq.core.VDJCLibrary;
import io.repseq.core.VDJCLibraryRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AllowNoArguments
public class ActionListLibraries implements Action {
    final AParameters parameters = new AParameters();

    @Override
    public void go(ActionHelper helper) {
        VDJCLibraryRegistry.getDefault().loadAllLibraries();
        System.out.println("Available libraries:");
        List<VDJCLibrary> loadedLibraries = new ArrayList<>(VDJCLibraryRegistry.getDefault().getLoadedLibraries());
        Collections.sort(loadedLibraries);
        for (VDJCLibrary library : loadedLibraries) {
            System.out.println(library.getLibraryId());
            System.out.println(VDJCLibraryRegistry.getDefault().getSpeciesNames(library.getTaxonId()));
        }
    }

    @Override
    public String command() {
        return "listLibraries";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "List all available library by scanning all library search paths.")
    public static final class AParameters extends ActionParameters {
    }
}
