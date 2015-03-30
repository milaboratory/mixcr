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

import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;

import java.util.UUID;

/**
 * Created by dbolotin on 17/07/14.
 */
class IO {
    public static class AlleleSerializer implements Serializer<Allele> {
        @Override
        public void write(PrimitivO output, Allele object) {
            throw new RuntimeException("Serializer only for knownReference serialization.");
        }

        @Override
        public Allele read(PrimitivI input) {
            throw new RuntimeException("Serializer only for knownReference serialization.");
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class AlleleIdSerializer implements Serializer<AlleleId> {
        @Override
        public void write(PrimitivO output, AlleleId object) {
            output.writeObject(object.containerUUID);
            output.writeObject(object.speciesAndLocus);
            output.writeUTF(object.name);
        }

        @Override
        public AlleleId read(PrimitivI input) {
            UUID uuid = input.readObject(UUID.class);
            SpeciesAndLocus speciesAndLocus = input.readObject(SpeciesAndLocus.class);
            String name = input.readUTF();
            return new AlleleId(uuid, speciesAndLocus, name);
        }

        @Override
        public boolean isReference() {
            return false;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class SpeciesAndLocusSerializer implements Serializer<SpeciesAndLocus> {
        @Override
        public void write(PrimitivO output, SpeciesAndLocus object) {
            output.writeVarInt(object.taxonId);
            output.writeObject(object.locus);
        }

        @Override
        public SpeciesAndLocus read(PrimitivI input) {
            int taxonId = input.readVarInt();
            return new SpeciesAndLocus(taxonId, input.readObject(Locus.class));
        }

        @Override
        public boolean isReference() {
            return false;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class ReferencePointSerializer implements Serializer<ReferencePoint> {
        @Override
        public void write(PrimitivO output, ReferencePoint object) {
            output.writeObject(object.basicPoint);
            output.writeInt(object.offset);
        }

        @Override
        public ReferencePoint read(PrimitivI input) {
            BasicReferencePoint brf = input.readObject(BasicReferencePoint.class);
            return new ReferencePoint(brf, input.readInt());
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class GeneFeatureReferenceRangeSerializer implements Serializer<GeneFeature.ReferenceRange> {
        @Override
        public void write(PrimitivO output, GeneFeature.ReferenceRange object) {
            output.writeObject(object.begin);
            output.writeObject(object.end);
        }

        @Override
        public GeneFeature.ReferenceRange read(PrimitivI input) {
            ReferencePoint begin = input.readObject(ReferencePoint.class);
            return new GeneFeature.ReferenceRange(begin, input.readObject(ReferencePoint.class));
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class GeneFeatureSerializer implements Serializer<GeneFeature> {
        @Override
        public void write(PrimitivO output, GeneFeature object) {
            output.writeObject(object.regions);
            // Saving this gene feature for the all subsequent serialization
            output.putKnownReference(object);
        }

        @Override
        public GeneFeature read(PrimitivI input) {
            GeneFeature object = new GeneFeature(input.readObject(GeneFeature.ReferenceRange[].class), true);
            // Saving this gene feature for the all subsequent deserialization
            input.putKnownReference(object);
            return object;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }
}
