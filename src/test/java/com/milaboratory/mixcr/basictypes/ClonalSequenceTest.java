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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.mutations.generator.MutationModels;
import com.milaboratory.core.mutations.generator.MutationsGenerator;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.test.TestUtil;
import com.milaboratory.util.BitArray;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well1024a;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ClonalSequenceTest {
    @Test
    public void testIsCompatible1() throws Exception {
        ClonalSequence c1 = create("AATC");
        ClonalSequence c2 = create("ATCT");
        Mutations<NucleotideSequence> muts = Mutations.decode("SA0T", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, muts));
        muts = Mutations.decode("I0T", NucleotideSequence.ALPHABET);
        Assert.assertFalse(c1.isCompatible(c2, muts));
    }

    @Test
    public void testIsCompatible2() throws Exception {
        RandomGenerator generator = new Well1024a();
        Mutations<NucleotideSequence> muts = Mutations.decode("", NucleotideSequence.ALPHABET);
        for (int i = 0; i < 100; i++) {
            ClonalSequence c1 = createRandom(5 + generator.nextInt(10), generator);
            Assert.assertTrue(c1.isCompatible(c1, muts));
        }
    }

    @Test
    public void testIsCompatible3() throws Exception {
        ClonalSequence c1 = create("AATC", "ATTCGC", "TT");
        ClonalSequence c2 = create("ATC", "ATCTCGC", "T");
        Mutations<NucleotideSequence> muts = Mutations.decode("DA0I5TDT11", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, muts));
    }

    @Test
    public void testIsCompatible4() throws Exception {
        RandomGenerator generator = new Well1024a();
        for (int i = 0; i < 1000; i++) {
            TestData td = createRandomTestDara(1 + generator.nextInt(10), generator);
            assertTestData(td);
            int rp = generator.nextInt(td.c1.getConcatenated().size() / 2);
            byte rl = td.c1.getConcatenated().getSequence().codeAt(rp);
            if (rl == 0) rl = 1;
            else rl = (byte) (rl - 1);
            assert rp <= td.c1.getConcatenated().size();
            Mutations<NucleotideSequence> incompatible =
                    td.mutations.combineWith(Mutations.decode(
                            "I" + rp + "" + NucleotideSequence.ALPHABET.codeToSymbol(rl)
                            , NucleotideSequence.ALPHABET));
            Assert.assertFalse(td.c1.isCompatible(td.c2, incompatible));
        }
    }

    @Test
    public void testIsCompatible5() throws Exception {
        RandomGenerator random = new Well1024a();
        ClonalSequence c1 = create("AAAAAAAAAA", "AAAAAAAAAAAAAA");
        ClonalSequence c2 = create("AAAAA", "AAAAAAA");
        testCompatiblePair(c1, c2, random);
        testCompatiblePair(c2, c1, random);
//        c2 = create("AAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAA");
//        testCompatiblePair(c1, c2, random);
//        testCompatiblePair(c2, c1, random);
//        c2 = create("AAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAA");
//        testCompatiblePair(c1, c2, random);
//        testCompatiblePair(c2, c1, random);
    }

    private static void testCompatiblePair(ClonalSequence c1, ClonalSequence c2, RandomGenerator random) {
        int delta = c1.getConcatenated().size() - c2.getConcatenated().size();
        int c1size = c1.getConcatenated().size();

        for (int t = 0; t < 100; ++t) {
            int k = 1 + random.nextInt(2);
            int deletions, insertions;
            if (delta > 0) { // c1 > c2
                insertions = k;
                deletions = delta + k;
            } else {
                // c1 < c2
                deletions = k;
                insertions = -delta + k;
            }

            int[] muts = new int[Math.abs(deletions + insertions)];
            int c = 0;
            BitArray usedDels = new BitArray(c1size);
            for (int i = 0; i < deletions; ++i) {
                int p = random.nextInt(c1size);
                usedDels.set(p);
                muts[c++] = Mutation.createDeletion(p, 0);
            }
            for (int i = 0; i < insertions; ++i) {
                int p;
                do {
                    p = random.nextInt(c1size);
                } while (usedDels.get(p));
                muts[c++] = Mutation.createInsertion(p, 0);
            }
            Arrays.sort(muts);
            Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, muts);
            assert mutations.getLengthDelta() + c1.getConcatenated().size() == c2.getConcatenated().size();
            Assert.assertTrue(c1.isCompatible(c2, mutations));
        }
    }

    @Test
    public void testIsCompatible5a() throws Exception {
        ClonalSequence c1 = create("AA", "AAAAA");
        ClonalSequence c2 = create("AAAA", "AAAAAAA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("I1ADA2I3AI4AI5AI6A", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible5b() throws Exception {
        ClonalSequence c1 = create("AAAA", "AAAAAAA");
        ClonalSequence c2 = create("AA", "AAAAA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("DA1DA2DA3DA4", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible5c() throws Exception {
        ClonalSequence c1 = create("AA", "AA");
        ClonalSequence c2 = create("AAAAA", "AAAAA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("I0AI0AI1AI1AI1AI1AI1AI1ADA2DA3", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible6() throws Exception {
        ClonalSequence c1 = create("AAAA", "AAA");
        ClonalSequence c2 = create("AA", "AAAAA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("DA0I3A", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible7() throws Exception {
        ClonalSequence c1 = create("AAA", "AA");
        ClonalSequence c2 = create("AA", "AA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("DA3", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible8() throws Exception {
        ClonalSequence c1 = create("AA", "AA");
        ClonalSequence c2 = create("AAA", "AA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("I3A", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible9() throws Exception {
        ClonalSequence c1 = create("AA", "AA");
        ClonalSequence c2 = create("AAAA", "AAAA");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("I0AI0ADA0I0AI0AI1ADA2I3A", NucleotideSequence.ALPHABET);
        Assert.assertTrue(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible10() {
        ClonalSequence c1 = create("CATGCACCGTC");
        ClonalSequence c2 = create("CATGCACCGC");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("DT9I11G", NucleotideSequence.ALPHABET);
        Assert.assertFalse(c1.isCompatible(c2, mutations));

    }

    @Test
    public void testIsCompatible11() {
        ClonalSequence c1 = create("TTCTACTCAGGTT", "ATTAACTTAG");
        ClonalSequence c2 = create("TTCTACTAGGTT", "ATTAACTAG");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("DC7DT20I22A", NucleotideSequence.ALPHABET);
//        System.out.println(c1.getConcatenated().size() + mutations.getLengthDelta());
//        System.out.println(c2.getConcatenated().size());
        Assert.assertFalse(c1.isCompatible(c2, mutations));
    }

    @Test
    public void testIsCompatible12() throws Exception {
        ClonalSequence c1 = create("ATAGC", "GGGCCAGATGAC", "GAAGGTTTCCATCTG");
        ClonalSequence c2 = create("ATC", "GTCAGATGAC", "GGGTTTCCATCTG");
        Mutations<NucleotideSequence> mutations
                = Mutations.decode("DA2DG3 DG7DC8 DA19DG20I32C", NucleotideSequence.ALPHABET);
        Assert.assertFalse(c1.isCompatible(c2, mutations));
    }

    private void assertTestData(TestData testData) {
        Assert.assertTrue(testData.c1.isCompatible(testData.c2, testData.mutations));
    }

    private ClonalSequence create(String... strings) {
        NSequenceWithQuality[] data = new NSequenceWithQuality[strings.length];
        for (int i = 0; i < strings.length; ++i) {
            NucleotideSequence s = new NucleotideSequence(strings[i]);
            SequenceQuality q = SequenceQuality.getUniformQuality((byte) 0, s.size());
            data[i] = new NSequenceWithQuality(s, q);
        }
        return new ClonalSequence(data, null);
    }

    private ClonalSequence createRandom(int size, RandomGenerator generator) {
        NSequenceWithQuality[] data = new NSequenceWithQuality[size];
        for (int i = 0; i < size; ++i) {
            NucleotideSequence s = TestUtil.randomSequence(NucleotideSequence.ALPHABET, generator, 2, 10);
            SequenceQuality q = SequenceQuality.getUniformQuality((byte) 0, s.size());
            data[i] = new NSequenceWithQuality(s, q);
        }
        return new ClonalSequence(data, null);
    }

    private TestData createRandomTestDara(int size, RandomGenerator random) {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        NSequenceWithQuality[] c1 = new NSequenceWithQuality[size];
        NSequenceWithQuality[] c2 = new NSequenceWithQuality[size];
        int offset = 0;
        for (int i = 0; i < size; ++i) {
            NucleotideSequence s1 = TestUtil.randomSequence(NucleotideSequence.ALPHABET, random, 5, 15);
            Mutations<NucleotideSequence> muts = MutationsGenerator.generateMutations(s1,
                    MutationModels.getEmpiricalNucleotideMutationModel(), 1, s1.size() - 1);
            NucleotideSequence s2 = muts.mutate(s1);
            c1[i] = new NSequenceWithQuality(s1, SequenceQuality.getUniformQuality((byte) 0, s1.size()));
            c2[i] = new NSequenceWithQuality(s2, SequenceQuality.getUniformQuality((byte) 0, s2.size()));
            builder.append(muts.move(offset));
            offset += s1.size();
        }
        return new TestData(new ClonalSequence(c1, null), new ClonalSequence(c2, null), builder.createAndDestroy());
    }

    private static final class TestData {
        final ClonalSequence c1, c2;
        final Mutations<NucleotideSequence> mutations;

        private TestData(ClonalSequence c1, ClonalSequence c2, Mutations<NucleotideSequence> mutations) {
            this.c1 = c1;
            this.c2 = c2;
            this.mutations = mutations;
        }
    }
}