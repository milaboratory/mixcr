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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.util.GlobalObjectMappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public final class FastaLocusBuilderParametersBundle {
    private final FastaLocusBuilderParameters v, d, j, c;

    @JsonCreator
    public FastaLocusBuilderParametersBundle(@JsonProperty("v") FastaLocusBuilderParameters v,
                                             @JsonProperty("d") FastaLocusBuilderParameters d,
                                             @JsonProperty("j") FastaLocusBuilderParameters j,
                                             @JsonProperty("c") FastaLocusBuilderParameters c) {
        if (v != null && v.getGeneType() != GeneType.Variable)
            throw new IllegalArgumentException();
        if (d != null && d.getGeneType() != GeneType.Diversity)
            throw new IllegalArgumentException();
        if (j != null && j.getGeneType() != GeneType.Joining)
            throw new IllegalArgumentException();
        if (c != null && c.getGeneType() != GeneType.Constant)
            throw new IllegalArgumentException();
        this.v = v;
        this.d = d;
        this.j = j;
        this.c = c;
    }

    public FastaLocusBuilderParameters getForGeneType(GeneType geneType) {
        switch (geneType) {
            case Variable:
                return v;
            case Diversity:
                return v;
            case Joining:
                return v;
            case Constant:
                return v;
        }
        throw new IllegalArgumentException();
    }

    public FastaLocusBuilderParameters getV() {
        return v;
    }

    public FastaLocusBuilderParameters getD() {
        return d;
    }

    public FastaLocusBuilderParameters getJ() {
        return j;
    }

    public FastaLocusBuilderParameters getC() {
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FastaLocusBuilderParametersBundle that = (FastaLocusBuilderParametersBundle) o;

        if (v != null ? !v.equals(that.v) : that.v != null) return false;
        if (d != null ? !d.equals(that.d) : that.d != null) return false;
        if (j != null ? !j.equals(that.j) : that.j != null) return false;
        return !(c != null ? !c.equals(that.c) : that.c != null);

    }

    @Override
    public int hashCode() {
        int result = v != null ? v.hashCode() : 0;
        result = 31 * result + (d != null ? d.hashCode() : 0);
        result = 31 * result + (j != null ? j.hashCode() : 0);
        result = 31 * result + (c != null ? c.hashCode() : 0);
        return result;
    }

    public static FastaLocusBuilderParametersBundle getBuiltInBundleByName(String name) {
        return builtIn.get(name);
    }

    /**
     * List of known parameters presets
     */
    private static final Map<String, FastaLocusBuilderParametersBundle> builtIn;

    static {
        Map<String, FastaLocusBuilderParametersBundle> map = null;
        try {
            InputStream is = FastaLocusBuilderParametersBundle.class.getClassLoader().getResourceAsStream("parameters/segment_importer_parameters.json");
            TypeReference<HashMap<String, FastaLocusBuilderParametersBundle>> typeRef
                    = new TypeReference<
                    HashMap<String, FastaLocusBuilderParametersBundle>
                    >() {
            };
            map = GlobalObjectMappers.ONE_LINE.readValue(is, typeRef);
        } catch (IOException ioe) {
            System.out.println("ERROR!");
            ioe.printStackTrace();
        }
        builtIn = map;
    }
}
