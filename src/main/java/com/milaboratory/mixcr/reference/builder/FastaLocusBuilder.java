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
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.Locus;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public void importAllelesFromFile(String fileName) throws IOException {
        try (InputStream stream = new BufferedInputStream(new FileInputStream(fileName))) {
            importAllelesFromStream(stream);
        }
    }

    public void importAllelesFromStream(InputStream stream) {
        // Saving in local variables for compactness of extraction code
        Pattern alleleNameExtractionPattern = parameters.getAlleleNameExtractionPattern();
        Pattern functionalGenePattern = parameters.getFunctionalAllelePattern();
        Pattern referenceAllelePattern = parameters.getReferenceAllelePattern();
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

            boolean isFirst = false, isReference;

            // Adding allele to corresponding gene
            GeneInfo gene;
            if ((gene = genes.get(geneName)) == null) {
                // If gene doesn't exist - create it
                genes.put(geneName, gene = new GeneInfo(geneName));
                // This allele is first for this gene
                isFirst = true;
            }

            // Calculating isReference flag
            if (referenceAllelePattern == null)
                isReference = isFirst;
            else
                isReference = referenceAllelePattern.matcher(rec.description).matches();

            // Creating allele info
            AlleleInfo alleleInfo = new AlleleInfo(geneName, alleleName, seq, isFunctional,
                    isReference, referencePoints);

            // Checking if this allele is unique
            if (gene.alleles.containsKey(alleleName)) {
                errorOrException("Duplicate records for allele " + alleleName);
                continue;
            }

            // Adding allele to gene
            gene.alleles.put(alleleName, alleleInfo);

            // Calculating severalReferenceAlleles flag
            if (alleleInfo.isReference && gene.reference != null && gene.reference.isReference)
                gene.severalReferenceAlleles = true;

            // If allele is first for tis gene, also add it to reference slot
            // Also reset this slot for first "reference" allele occurred
            if (isFirst || (!gene.reference.isReference && alleleInfo.isReference))
                gene.reference = alleleInfo;
        }
    }

    public Mutations<NucleotideSequence> alignAlleles(AlleleInfo a1, AlleleInfo a2) {
        NucleotideSequence seq1 = a1.baseSequence;
        NucleotideSequence seq2 = a2.baseSequence;
        int[] ref1 = a1.referencePoints;
        int[] ref2 = a2.referencePoints;
        MutationsBuilder<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        int prev1 = 0, prev2 = 0, curr1, curr2;
        for (int i = 0; i <= ref1.length; i++) {
            curr1 = i == ref1.length ? seq1.size() : ref1[i];
            curr2 = i == ref2.length ? seq2.size() : ref2[i];
            if (curr1 == -1 || curr2 == -1)
                continue;
            if (curr1 - prev1 == 0 && curr2 - prev2 == 0)
                continue;
            Alignment<NucleotideSequence> alignment = Aligner.alignGlobal(parameters.getScoring(),
                    seq1.getRange(prev1, curr1), seq2.getRange(prev2, curr2));
            mutations.append(alignment.getAbsoluteMutations().move(prev1));
            prev1 = curr1;
            prev2 = curr2;
        }
        return mutations.createAndDestroy();
    }

    public void compile() {
        for (GeneInfo gene : genes.values()) {
            gene.compile();
        }
    }

    private final class GeneInfo {
        final String geneName;
        final Map<String, AlleleInfo> alleles = new HashMap<>();
        final List<AlleleInfo> finalList = new ArrayList<>();
        AlleleInfo reference;
        boolean severalReferenceAlleles = false;

        public GeneInfo(String geneName) {
            this.geneName = geneName;
        }

        public void compile() {
            if (parameters.doAlignAlleles()) {
                // Find reference allele
                AlleleInfo reference = this.reference;
                if (!reference.isReference)
                    errorOrException("No reference allele for gene " + geneName);
                if (severalReferenceAlleles)
                    errorOrException("Several reference alleles for " + geneName);
                finalList.add(reference);
                for (AlleleInfo allele : alleles.values())
                    if (allele != reference) {
                        allele.reference = reference;
                        allele.mutations = alignAlleles(reference, allele);
                        finalList.add(allele);
                    }
            } else {
                // Just creates final list
                finalList.add(reference);
                for (AlleleInfo allele : alleles.values())
                    if (allele != reference)
                        finalList.add(allele);
            }
        }
    }

    private final class AlleleInfo {
        final String geneName, alleleName;
        final NucleotideSequence baseSequence;
        final boolean isFunctional, isReference;
        final int[] referencePoints;
        AlleleInfo reference;
        Mutations<NucleotideSequence> mutations;

        public AlleleInfo(String geneName, String alleleName, NucleotideSequence baseSequence,
                          boolean isFunctional, boolean isReference, int[] referencePoints) {
            this.geneName = geneName;
            this.alleleName = alleleName;
            this.baseSequence = baseSequence;
            this.isFunctional = isFunctional;
            this.isReference = isReference;
            this.referencePoints = referencePoints;
        }
    }
}
