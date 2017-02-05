/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.tests;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import io.repseq.core.*;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TargetBuilder {
    public static final class VDJCGenes {
        public final VDJCGene v, d, j, c;

        public VDJCGenes(VDJCLibrary library, String v, String d, String j, String c) {
            this.v = library.getSafe(v);
            this.d = library.getSafe(d);
            this.j = library.getSafe(j);
            this.c = library.getSafe(c);
        }

        public VDJCGenes(VDJCGene v, VDJCGene d, VDJCGene j, VDJCGene c) {
            this.v = v;
            this.d = d;
            this.j = j;
            this.c = c;
        }

        public VDJCGene geneById(int id) {
            switch (id) {
                case 0:
                    return v;
                case 1:
                    return d;
                case 2:
                    return j;
                case 3:
                    return c;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public VDJCGene geneByGeneType(GeneType gt) {
            switch (gt) {
                case Variable:
                    return v;
                case Diversity:
                    return d;
                case Joining:
                    return j;
                case Constant:
                    return c;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    final static Pattern preProcessPattern = Pattern.compile("\\*(\\d+)|(.)");
    final static Pattern modelParserPattern = Pattern.compile("\'([ATGCatgc]+)\'|\\{([A-za-z()0-9\\-]+)}|\\{([A-za-z()0-9\\-]+):([A-za-z()0-9\\-]+)}|([Vv]+)|([Dd]+)|([Jj]+)|([Cc]+)|(N+)| ");
    final static int atgcGroupId = 1;
    final static int refPointGroupId = 2;
    final static int geneFeaturePoint1GroupId = 3;
    final static int geneFeaturePoint2GroupId = 4;
    final static int vGroupId = 5;
    final static int dGroupId = 6;
    final static int jGroupId = 7;
    final static int cGroupId = 8;
    final static int nGroupId = 9;

    public static String preProcessModel(String model) {
        StringBuilder processedModel = new StringBuilder();
        Matcher matcher = preProcessPattern.matcher(model);
        String previousGroup = null;
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                int n = Integer.decode(matcher.group(1)) - 1;
                for (int i = 0; i < n; i++)
                    processedModel.append(previousGroup);
            } else
                processedModel.append(previousGroup = matcher.group());
        }
        return processedModel.toString();
    }


    public static NucleotideSequence generateSequence(VDJCGenes genes, String model, RandomGenerator rg) {
        // Pre-processing
        model = preProcessModel(model);

        // Building sequence
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        //builder.ensureCapacity(model.length());

        Matcher matcher = modelParserPattern.matcher(model);
        ReferencePoint leftPoint = null;
        ReferencePoint rightPoint;
        int prevPosition = 0;
        while (matcher.find()) {
            if (matcher.start() != prevPosition)
                throw new IllegalArgumentException("Can't parse " + model.substring(prevPosition, matcher.start()));

            prevPosition = matcher.end();
            String rpGroup = matcher.group(refPointGroupId);
            String gf1Group = matcher.group(geneFeaturePoint1GroupId);
            String gf2Group = matcher.group(geneFeaturePoint2GroupId);
            String nGroup = matcher.group(nGroupId);
            String atgcGroup = matcher.group(atgcGroupId);
            if (rpGroup != null)
                leftPoint = ReferencePoint.parse(rpGroup);
            else if (gf1Group != null) {
                leftPoint = ReferencePoint.parse(gf1Group);
                rightPoint = ReferencePoint.parse(gf2Group);
                GeneFeature geneFeature = new GeneFeature(leftPoint, rightPoint);
                VDJCGene gene = genes.geneByGeneType(geneFeature.getGeneType());
                NucleotideSequence seqGF = gene.getFeature(geneFeature);
                if (seqGF == null)
                    throw new RuntimeException("No sequence for feature " + geneFeature);
                builder.append(seqGF);
                leftPoint = rightPoint;
            } else if (nGroup != null)
                for (int j = 0; j < nGroup.length(); j++)
                    builder.append((byte) rg.nextInt(4));
            else if (atgcGroup != null)
                builder.append(new NucleotideSequence(atgcGroup));
            else {
                for (int i = 0; i < 4; i++) {
                    GeneType geneType = GeneType.VDJC_REFERENCE[i];
                    String group = matcher.group(i + vGroupId);
                    if (group != null) {
                        if (leftPoint == null || leftPoint.getGeneType() != geneType)
                            throw new IllegalArgumentException("No reference point for " + group);
                        rightPoint = leftPoint.move(group.length());
                        GeneFeature geneFeature = new GeneFeature(leftPoint, rightPoint);
                        VDJCGene gene = genes.geneByGeneType(geneType);
                        NucleotideSequence seqGF = gene.getFeature(geneFeature);
                        if (seqGF == null)
                            throw new RuntimeException("No sequence for feature " + geneFeature);
                        for (int j = 0; j < seqGF.size(); j++) {
                            char ch = group.charAt(j);
                            byte n = seqGF.codeAt(j);
                            if (Character.isLowerCase(ch))
                                builder.append((byte) ((n + 1 + rg.nextInt(3)) % 3));
                            else
                                builder.append(n);
                        }
                    }
                }
            }
        }

        if (model.length() != prevPosition)
            throw new IllegalArgumentException("Can't parse " + model.substring(prevPosition, model.length()));

        return builder.createAndDestroy();
    }
}
