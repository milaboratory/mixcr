/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis.additive;

import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.GeneFeature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public class AAProperties {
    /** Compute property normalized on sequence length */
    public static double computeNormalized(AAProperty property, VDJCObject obj, GeneFeature gf) {
        AminoAcidSequence seq = obj.getAAFeature(gf);
        if (seq == null)
            return Double.NaN;
        double r = 0;
        for (int i = 0; i < seq.size(); ++i)
            r += getAAProperty(seq.codeAt(i), property);
        return r / seq.size();
    }

    /** Compute property based on selection */
    public static double compute(AAProperty property, VDJCObject obj, GeneFeature gf,
                                 Adjustment alignment, int nLetters) {
        AminoAcidSequence seq = obj.getAAFeature(gf);
        if (seq == null)
            return Double.NaN;

        int from, to;
        if (nLetters > seq.size()) {
            from = 0;
            to = seq.size();
        } else
            switch (alignment) {
                case Leading:
                    from = 0;
                    to = nLetters;
                    break;
                case Trailing:
                    from = seq.size() - nLetters;
                    to = seq.size();
                    break;
                case LeadingCenter:
                    from = (seq.size() - nLetters) / 2;
                    to = from + nLetters;
                    break;
                case TrailingCenter:
                    from = (seq.size() - nLetters + 1) / 2;
                    to = from + nLetters;
                    break;
                default:
                    throw new RuntimeException();
            }
        double r = 0;
        for (int i = from; i < to; ++i)
            r += getAAProperty(seq.codeAt(i), property);
        return r;
    }

    public enum Adjustment {
        Leading, Trailing, LeadingCenter, TrailingCenter
    }

    public static double getAAProperty(byte aa, AAProperty property) {
        return aaPropertiesTable[aa][property.index];
    }

    public enum AAProperty {
        Hydropathy(0),
        Charge(1),
        Polarity(2),
        Volume(3),
        Strength(4),
        MjEnergy(5),
        Kf1(6),
        Kf2(7),
        Kf3(8),
        Kf4(9),
        Kf5(10),
        Kf6(11),
        Kf7(12),
        Kf8(13),
        Kf9(14),
        Kf10(15),
        Rim(16),
        Surface(17),
        Turn(18),
        Alpha(19),
        Beta(20),
        Core(21),
        Disorder(22),
        N2Strength(23),
        N2Hydrophobicity(24),
        N2Volume(25),
        N2Surface(26);

        private final int index;

        AAProperty(int index) {
            this.index = index;
        }

        static AAProperty parse(String str) {
            for (AAProperty v : values())
                if (v.toString().equalsIgnoreCase(str))
                    return v;
            throw new IllegalArgumentException("no such element " + str);
        }
    }

    static final double[][] aaPropertiesTable = loadAAPropTableFromCsv();

    private static double[][] loadAAPropTableFromCsv() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(AAProperties.class.getResourceAsStream("/postanalysis/aa_properties.csv")))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            AAProperty[] allProperties = AAProperty.values();
            double[][] result = new double[AminoAcidSequence.ALPHABET.size()][allProperties.length];
            String[] header = lines.get(0).split(",");
            for (int i = 1; i < lines.size(); i++) {
                String[] line = lines.get(i).split(",");

                AminoAcidSequence aaSeq = new AminoAcidSequence(line[0]);
                if (aaSeq.size() != 1)
                    throw new IllegalArgumentException();
                byte aa = aaSeq.codeAt(0);

                for (int j = 1; j < line.length; j++) {
                    AAProperty prop = AAProperty.parse(header[j]);
                    result[aa][prop.index] = Double.parseDouble(line[j]);
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
