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
package com.milaboratory.mixcr.reference.builder;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.Locus;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.milaboratory.mixcr.reference.builder.BuilderUtils.*;

/**
 * Builds MiXCR format LociLibrary file from
 */
public class FastaLocusBuilder {
    /**
     * Target locus
     */
    private final Locus locus;
    /**
     * Parameters
     */
    private final FastaLocusBuilderParameters parameters;
    /**
     * Genes by name
     */
    private final Map<String, GeneInfo> genes = new HashMap<>();
    /**
     * Stream for logging warning and error messages
     */
    private PrintStream loggingStream = System.out;
    /**
     * Stream for writing final report to
     */
    private PrintStream finalReportStream = System.out;
    /**
     * Determines whether builder should terminate on error
     */
    private boolean exceptionOnError = true;
    /**
     * Determines whether builder should process alleles with non-standard names
     */
    private boolean allowNonStandardAlleleNames = false;

    public FastaLocusBuilder(Locus locus, FastaLocusBuilderParameters parameters) {
        this.locus = locus;
        this.parameters = parameters;
    }

    public FastaLocusBuilder setLoggingStream(PrintStream loggingStream) {
        this.loggingStream = loggingStream;
        return this;
    }

    public FastaLocusBuilder setFinalReportStream(PrintStream finalReportStream) {
        this.finalReportStream = finalReportStream;
        return this;
    }

    public FastaLocusBuilder noExceptionOnError() {
        this.exceptionOnError = false;
        return this;
    }

    public FastaLocusBuilder allowNonStandardAlleleNames() {
        this.allowNonStandardAlleleNames = true;
        return this;
    }

    private void errorOrException(String message) {
        if (exceptionOnError)
            throw new FastaLocusBuilderException(message);
        else
            error(message);
    }

    private void error(String line) {
        log("Error: " + line);
    }

    private void warning(String line) {
        log("Warning: " + line);
    }

    private void log(String line) {
        if (loggingStream != null)
            loggingStream.println(line);
    }

    private void reportLine(String line) {
        if (finalReportStream != null)
            finalReportStream.println(line);
    }

    public void importAllelesFromStream(InputStream stream) {
        // Saving in local variables for compactness of extraction code
        Pattern alleleNameExtractionPattern = parameters.getAlleleNameExtractionPattern();
        Pattern functionalGenePattern = parameters.getFunctionalAllelePattern();
        int[] referencePointPositions = parameters.getReferencePointPositions();

        for (FastaReader.RawFastaRecord rec :
                CUtils.it(new FastaReader<>(stream, null).asRawRecordsPort())) {
            //Extracting allele name from header
            Matcher matcher = alleleNameExtractionPattern.matcher(rec.description);
            String alleleName;
            if (matcher.find())
                alleleName = matcher.group(1);
            else {
                String errorMessage = "Header does'n contain allele name pattern: " + rec.description;
                errorOrException(errorMessage);
                continue;
            }

            // Parsing allele name
            matcher = ALLELE_NAME_PATTERN.matcher(alleleName);

            String geneName;

            // Checking
            if (matcher.matches()) {
                // Extracting gene name
                geneName = matcher.group(GENE_NAME_GROUP);

                // Checking locus decoded from allele name
                if (!matcher.group(LOCUS_GROUP).equalsIgnoreCase(locus.toString()))
                    warning("Allele from different locus(?): " + alleleName);

                // Checking gene type decoded from allele name
                if (Character.toUpperCase(matcher.group(GENE_TYPE_LETTER_GROUP).charAt(0)) !=
                        parameters.getGeneType().getLetter())
                    warning("Allele of different gene type(?): " + alleleName);
            } else {
                String errorMessage = "Allele name doesn't match standard pattern: " + alleleName;
                if (!allowNonStandardAlleleNames)
                    throw new FastaLocusBuilderException(errorMessage);
                else {
                    geneName = alleleName;
                    alleleName += "*00";
                    errorMessage += ". Changed to: " + alleleName;
                    error(errorMessage);
                }
            }

            // Detecting whether allele is functional
            boolean isFunctional = functionalGenePattern.matcher(rec.description).find();

            // Parsing sequence
            StringWithMapping seqWithPositionMapping = StringWithMapping.removeSymbol(rec.sequence, parameters.getPaddingChar());
            NucleotideSequence seq = new NucleotideSequence(seqWithPositionMapping.getModifiedString());

            // If sequence contain wildcards, skip it
            if (seq.containsWildcards()) {
                warning("Skipping " + alleleName + " because it's sequence contains wildcards.");
                continue;
            }

            // Calculating reference points
            int[] referencePoints = new int[referencePointPositions.length];
            for (int i = 0; i < referencePointPositions.length; i++)
                referencePoints[i] = seqWithPositionMapping.convertPosition(referencePointPositions[i]);

            // Creating allele info
            AlleleInfo alleleInfo = new AlleleInfo(geneName, seq, isFunctional, referencePoints);

            // Adding allele to corresponding gene
            GeneInfo gene;
            if ((gene = genes.get(geneName)) == null)
                // If gene doesn't exist - create it
                genes.put(geneName, gene = new GeneInfo(geneName));

            // Checking if this allele is unique
            if (gene.alleles.containsKey(alleleName)) {
                errorOrException("Duplicate records for allele " + alleleName);
                continue;
            }

            // Adding allele to gene
            gene.alleles.put(alleleName, alleleInfo);
        }
    }

    public static final class GeneInfo {
        final String geneName;
        final Map<String, AlleleInfo> alleles = new HashMap<>();

        public GeneInfo(String geneName) {
            this.geneName = geneName;
        }
    }

    public static final class AlleleInfo {
        final String geneName;
        final NucleotideSequence baseSequence;
        final boolean isFunctional;
        final int[] referencePoints;

        public AlleleInfo(String geneName, NucleotideSequence baseSequence, boolean isFunctional, int[] referencePoints) {
            this.geneName = geneName;
            this.baseSequence = baseSequence;
            this.isFunctional = isFunctional;
            this.referencePoints = referencePoints;
        }
    }
}
