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

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SequenceBaseTest {
    @Test(expected = IllegalArgumentException.class)
    public void test1e() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 20, new NucleotideSequence("ATT"));
    }

    @Test
    public void test1() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 30, new NucleotideSequence("ATTACACA"));
        base.put("A2", 0, new NucleotideSequence("TATAGACATAAGCA"));
        assertNull(base.get("A1", new Range(21, 24)));
        assertNull(base.get("A1", new Range(29, 32)));
        assertNull(base.get("A3", new Range(29, 32)));
        assertEquals(new NucleotideSequence("TAGA"), base.get("A1", new Range(12, 16)));
        assertEquals(new NucleotideSequence("TCTA"), base.get("A1", new Range(16, 12)));
        assertEquals(new NucleotideSequence("GACA"), base.get("A2", new Range(4, 8)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test2e() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 20, new NucleotideSequence("TACATA"));
    }

    @Test
    public void test2() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 20, new NucleotideSequence("CACATA"));
        assertEquals(new NucleotideSequence("ACACA"), base.get("A1", new Range(19, 24)));
    }

    @Test
    public void test3() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 20, new NucleotideSequence("CACATA"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        assertEquals(new NucleotideSequence("ACACA"), base.get("A1", new Range(19, 24)));
    }

    @Test
    public void test4() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 18, new NucleotideSequence("CAC"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        assertEquals(new NucleotideSequence("ATTAGACACAC"), base.get("A1", new Range(10, 21)));
    }

    @Test
    public void test5() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 18, new NucleotideSequence("CAC"));
        assertEquals(new NucleotideSequence("ATTAGACACAC"), base.get("A1", new Range(10, 21)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test2em() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 100, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 20, new NucleotideSequence("TACATA"));
    }

    @Test
    public void test2m() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 100, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 20, new NucleotideSequence("CACATA"));
        assertEquals(new NucleotideSequence("ACACA"), base.get("A1", new Range(19, 24)));
        assertEquals(new NucleotideSequence("TTAG"), base.get("A1", new Range(101, 105)));
    }

    @Test
    public void test3m() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 100, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 20, new NucleotideSequence("CACATA"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        assertEquals(new NucleotideSequence("ACACA"), base.get("A1", new Range(19, 24)));
        assertEquals(new NucleotideSequence("TTAG"), base.get("A1", new Range(101, 105)));
    }

    @Test
    public void test4m() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 100, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 18, new NucleotideSequence("CAC"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        assertEquals(new NucleotideSequence("ATTAGACACAC"), base.get("A1", new Range(10, 21)));
        assertEquals(new NucleotideSequence("TTAG"), base.get("A1", new Range(101, 105)));
    }

    @Test
    public void test5m() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A1", 100, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 10, new NucleotideSequence("ATTAGACACACAC"));
        base.put("A1", 18, new NucleotideSequence("CAC"));
        assertEquals(new NucleotideSequence("ATTAGACACAC"), base.get("A1", new Range(10, 21)));
        assertEquals(new NucleotideSequence("TTAG"), base.get("A1", new Range(101, 105)));
    }
}
