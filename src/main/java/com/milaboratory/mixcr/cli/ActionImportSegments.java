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
package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.mixcr.reference.builder.FastaLocusBuilder;
import com.milaboratory.mixcr.reference.builder.FastaLocusBuilderParametersBundle;
import io.repseq.reference.*;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ActionImportSegments implements Action {
    private final AParameters params = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        Path outputFile = params.getOutputFile();
        Path parent = outputFile.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        boolean outputExists = Files.exists(outputFile);
        final int taxonID = params.getTaxonID();
        Chain chain = params.getLocus();
        final String[] commonNames = params.getCommonNames();
        final Set<String> commonNamesSet = new HashSet<>();
        for (String cn : commonNames)
            commonNamesSet.add(cn);
        if (outputExists) {
            LociLibrary ll = LociLibraryReader.read(outputFile.toFile(), false);
            final SpeciesAndChain sl = new SpeciesAndChain(taxonID, chain);
            if (params.getForce()) {
                LociLibraryIOUtils.LociLibraryFilter filter = new LociLibraryIOUtils.LociLibraryFilter() {
                    boolean remove = false;

                    @Override
                    public boolean speciesName(int taxonId1, String name1) {
                        //if (taxonID == taxonId1)
                        //    for (String cn : commonNames)
                        //        if (name1.equals(cn))
                        //            return false;
                        if (commonNamesSet.contains(name1)) {
                            if (taxonId1 != taxonID)
                                return false;
                            else
                                commonNamesSet.remove(name1);
                        }
                        return true;
                    }

                    @Override
                    public boolean beginLocus(LocusContainer container) {
                        return !(remove = container.getSpeciesAndChain().equals(sl));
                    }

                    @Override
                    public boolean endLocus(LocusContainer container) {
                        if (remove) {
                            remove = false;
                            return false;
                        }
                        return true;
                    }

                    @Override
                    public boolean allele(Allele allele) {
                        return !remove;
                    }
                };
                LociLibraryIOUtils.filterLociLibrary(outputFile.toFile(), filter);
            } else {
                if (ll.getLocus(sl) != null) {
                    System.err.println("Specified file (" + outputFile + ") already contain record for: " + sl + "; use -f to overwrite.");
                    return;
                }
                for (String commonName : commonNames) {
                    int id = ll.getSpeciesTaxonId(commonName);
                    if (id != -1)
                        if (id != taxonID) {
                            System.err.println("Specified file (" + outputFile + ") contains other mapping for common species name: " + commonName + " -> " + id + "; use -f to overwrite.");
                            return;
                        } else
                            commonNamesSet.remove(commonName);
                }
            }
        }

        FastaLocusBuilderParametersBundle bundle = getBuilderParameters();

        try (PrintStream ps = (params.report == null ? System.out : new PrintStream(new FileOutputStream(params.report,
                true)))) {
            FastaLocusBuilder vBuilder, dBuilder = null, jBuilder;

            vBuilder = new FastaLocusBuilder(chain, bundle.getV())
                    .setLoggingStream(ps).setFinalReportStream(ps)
                    .printErrorsAndWarningsToSTDOUT().noExceptionOnError();
            vBuilder.importAllelesFromFile(params.getV());

            jBuilder = new FastaLocusBuilder(chain, bundle.getJ())
                    .setLoggingStream(ps).setFinalReportStream(ps)
                    .printErrorsAndWarningsToSTDOUT().noExceptionOnError();
            jBuilder.importAllelesFromFile(params.getJ());

            if (params.getD() != null) {
                dBuilder = new FastaLocusBuilder(chain, bundle.getD())
                        .setLoggingStream(ps).setFinalReportStream(ps)
                        .printErrorsAndWarningsToSTDOUT().noExceptionOnError();
                dBuilder.importAllelesFromFile(params.getD());
            }

            System.out.println("Processing...");

            vBuilder.compile();
            jBuilder.compile();
            if (dBuilder != null)
                dBuilder.compile();

            System.out.println("Writing report.");

            vBuilder.printReport();
            jBuilder.printReport();
            if (dBuilder != null)
                dBuilder.printReport();

            System.out.println("Writing library file.");

            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile.toFile(), true))) {
                LociLibraryWriter writer = new LociLibraryWriter(bos);

                // Write magic bytes if we create file from scratch
                if (!outputExists)
                    writer.writeMagic();

                writer.writeBeginOfLocus(taxonID, chain);
                vBuilder.writeAlleles(writer);
                jBuilder.writeAlleles(writer);
                if (dBuilder != null)
                    dBuilder.writeAlleles(writer);
                writer.writeEndOfLocus();
                for (String cn : commonNamesSet)
                    writer.writeCommonSpeciesName(taxonID, cn);
            }

            System.out.println("Checking.");

            LociLibrary ll = LociLibraryReader.read(outputFile.toFile(), false);

            System.out.println("Segments successfully imported.");

            if (params.doPrintInfo()) {
                System.out.println("Resulting file contains following records:");
                for (LocusContainer locusContainer : ll.getLoci())
                    System.out.println(locusContainer.getSpeciesAndChain() + ": " +
                            locusContainer.getAllAlleles().size() + " records");
            }
        }
    }

    public FastaLocusBuilderParametersBundle getBuilderParameters() {
        FastaLocusBuilderParametersBundle bundle =
                FastaLocusBuilderParametersBundle.getBuiltInBundleByName(params.getBuilderParametersName());
        if (bundle == null)
            throw new ParameterException("Can't find parameters with name: " + params.getBuilderParametersName());
        return bundle;
    }

    @Override
    public String command() {
        return "importSegments";
    }

    @Override
    public ActionParameters params() {
        return params;
    }

    @Parameters(commandDescription = "Imports segment sequences from fasta file (e.g. formatted as IMGT reference " +
            "sequences with IMGT gaps).",
            optionPrefixes = "-")
    public static final class AParameters extends ActionParameters {
        //@Parameter(description = "input_file_V.fasta input_file_J.fasta [input_file_D.fasta]")
        //public List<String> parameters;

        @Parameter(description = "Import parameters (name of built-in parameter set or a name of JSON file with " +
                "custom import parameters).", names = {"-p", "--parameters"})
        public String builderParametersName = "imgt";

        @Parameter(description = "Force overwrite data.",
                names = {"-f"})
        public Boolean force;

        @Parameter(description = "Print resulting file information.",
                names = {"-i"})
        public Boolean printInfo;

        @Parameter(description = "Input *.fasta file with V genes.",
                names = {"-v"})
        public String v;

        @Parameter(description = "Input *.fasta file with J genes.",
                names = {"-j"})
        public String j;

        @Parameter(description = "Input *.fasta file with D genes.",
                names = {"-d"})
        public String d;

        @Parameter(description = "Chain (e.g. IGH, TRB etc...)",
                names = {"-l", "--chain"})
        public String locus;

        @Parameter(description = "Species taxonID and it's common names (e.g. 9606:human:HomoSapiens:hsa)",
                names = {"-s", "--species"})
        public String species;

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "Output file (optional, default path is ~/.mixcr/local.ll", //, or $MIXCR_PATH/system.ll if -g option specified)",
                names = {"-o", "--output"})
        public String output = null;

        //@Parameter(description = "Add to system-wide loci library ($MIXCR_PATH/system.ll).",
        //        names = {"-s", "--system"}, hidden = true)
        //public Boolean global;

        public String getBuilderParametersName() {
            return builderParametersName;
        }

        public Chain getLocus() {
            return Chain.fromId(locus);
        }

        public boolean doPrintInfo() {
            return printInfo != null && printInfo;
        }

        public int getTaxonID() {
            String[] split = species.split("\\:");
            return Integer.parseInt(split[0]);
        }

        public String[] getCommonNames() {
            String[] split = species.split("\\:");
            return Arrays.copyOfRange(split, 1, split.length);
        }

        public Path getOutputFile() {
            if (output != null)
                return Paths.get(output);
            return Util.getLocalSettingsDir().resolve("local.ll");
        }

        public String getV() {
            return v;
        }

        public String getJ() {
            return j;
        }

        public String getD() {
            return d;
        }

        public boolean getForce() {
            return force != null && force;
        }

        @Override
        public void validate() {
            if (v == null)
                throw new ParameterException("Please specify file for V gene.");

            if (j == null)
                throw new ParameterException("Please specify file for J gene.");

            if (locus == null)
                throw new ParameterException("Please specify chain (e.g. \"-l TRB\").");
            if (Chain.fromId(locus) == null)
                throw new ParameterException("Unrecognized chain: " + locus);

            if (species == null)
                throw new ParameterException("Please specify species.");

            try {
                Integer.parseInt(species.split(":")[0]);
            } catch (NumberFormatException e) {
                throw new ParameterException("Malformed species name.");
            }

            super.validate();
        }
    }
}
