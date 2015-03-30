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

import com.milaboratory.primitivio.annotations.Serializable;

import java.util.UUID;

@Serializable(by = IO.AlleleIdSerializer.class)
public final class AlleleId implements Comparable<AlleleId> {
    static final long serialVersionUID = 1L;
    final UUID containerUUID;
    final SpeciesAndLocus speciesAndLocus;
    final String name;

    public AlleleId(UUID containerUUID, SpeciesAndLocus speciesAndLocus, String name) {
        if (containerUUID == null || speciesAndLocus == null || name == null)
            throw new NullPointerException();

        this.containerUUID = containerUUID;
        this.speciesAndLocus = speciesAndLocus;
        this.name = name;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public UUID getContainerUUID() {
        return containerUUID;
    }

    public SpeciesAndLocus getSpeciesAndLocus() {
        return speciesAndLocus;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlleleId)) return false;

        AlleleId alleleId = (AlleleId) o;

        if (!containerUUID.equals(alleleId.containerUUID)) return false;
        if (!name.equals(alleleId.name)) return false;
        if (!speciesAndLocus.equals(alleleId.speciesAndLocus)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = containerUUID.hashCode();
        result = 31 * result + speciesAndLocus.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public int compareTo(AlleleId o) {
        if (this == o)
            return 0;
        int r;
        if ((r = containerUUID.compareTo(o.containerUUID)) != 0)
            return r;
        if ((r = name.compareTo(o.name)) != 0)
            return r;
        return speciesAndLocus.compareTo(o.speciesAndLocus);
    }
}
