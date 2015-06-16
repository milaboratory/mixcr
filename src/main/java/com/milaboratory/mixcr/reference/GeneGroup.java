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


import static com.milaboratory.mixcr.reference.Locus.*;

/**
 * Enum for main recombination segment groups. TRBV, TRBJ, etc...
 *
 * @author Bolotin Dmitriy (bolotin.dmitriy@gmail.com)
 * @author Shugay Mikhail (mikhail.shugay@gmail.com)
 */
public enum GeneGroup implements java.io.Serializable {
    //TRA
    TRAC("TRAC", GeneType.Constant, TRA), TRAJ("TRAJ", GeneType.Joining, TRA),
    TRAV("TRAV", GeneType.Variable, TRA),

    //TRB
    TRBC("TRBC", GeneType.Constant, TRB), TRBD("TRBD", GeneType.Diversity, TRB),
    TRBJ("TRBJ", GeneType.Joining, TRB), TRBV("TRBV", GeneType.Variable, TRB),

    //TRG
    TRGC("TRGC", GeneType.Constant, TRG), TRGJ("TRGJ", GeneType.Joining, TRG),
    TRGV("TRGV", GeneType.Variable, TRG),

    //TRD
    TRDC("TRDC", GeneType.Constant, TRD), TRDD("TRDD", GeneType.Diversity, TRD),
    TRDJ("TRDJ", GeneType.Joining, TRD), TRDV("TRDV", GeneType.Variable, TRD),

    //IGL
    IGLC("IGLC", GeneType.Constant, IGL), IGLJ("IGLJ", GeneType.Joining, IGL),
    IGLV("IGLV", GeneType.Variable, IGL),

    //IGK
    IGKC("IGKC", GeneType.Constant, IGK), IGKJ("IGKJ", GeneType.Joining, IGK),
    IGKV("IGKV", GeneType.Variable, IGK),

    //IGH
    IGHC("IGHC", GeneType.Constant, IGH), IGHD("IGHD", GeneType.Diversity, IGH),
    IGHJ("IGHJ", GeneType.Joining, IGH), IGHV("IGHV", GeneType.Variable, IGH);

    final String id;
    final GeneType type;
    final Locus locus;

    private GeneGroup(String id, GeneType type, Locus locus) {
        this.id = id;
        this.type = type;
        this.locus = locus;
    }

    public GeneType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Locus getLocus() {
        return locus;
    }

    public static GeneGroup get(Locus g, GeneType type) {
        for (GeneGroup sg : values())
            if (sg.locus == g && sg.type == type)
                return sg;
        return null;
    }

    public static GeneGroup getFromName(String name) {
        name = name.toUpperCase();
        for (GeneGroup group : values())
            if (group.id.equals(name))
                return group;
        return null;
    }
}
