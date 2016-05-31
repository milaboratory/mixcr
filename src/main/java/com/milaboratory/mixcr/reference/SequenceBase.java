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
import com.milaboratory.util.RangeMap;

import java.util.HashMap;
import java.util.Map;

import static java.util.Map.Entry;

/**
 * Storage of fragmented sequences.
 */
public final class SequenceBase {
    private final Map<String, SequenceContainer> library = new HashMap<>();

    public void put(String accession, int from, NucleotideSequence sequence) {
        SequenceContainer container = library.get(accession);
        if (container == null)
            library.put(accession, new SequenceContainer(from, sequence));
        else
            container.put(from, sequence);
    }

    public NucleotideSequence get(String accession, Range range) {
        SequenceContainer c = library.get(accession);
        if (c == null)
            return null;
        return c.get(range);
    }

    public Range getAvailableRange(String accession, Range range) {
        SequenceContainer c = library.get(accession);
        if (c == null)
            return null;
        return c.getAvailableRange(range);
    }

    public boolean isEmpty() {
        return library.isEmpty();
    }

    private static final class SequenceContainer {
        private Range singleRange;
        private NucleotideSequence singleSequence;
        private RangeMap<NucleotideSequence> map;

        SequenceContainer(int begin, NucleotideSequence seq) {
            singleRange = new Range(begin, begin + seq.size());
            singleSequence = seq;
        }

        void put(int begin, NucleotideSequence sequence) {
            Range r = new Range(begin, begin + sequence.size());
            if (singleRange != null) {
                if (singleRange.intersectsWith(r)) {
                    // Checking
                    Range intersection = singleRange.intersection(r);
                    NucleotideSequence intersectionSequence = get(intersection);
                    if (!intersectionSequence.equals(sequence.getRange(intersection.move(-begin))))
                        throw new IllegalArgumentException();

                    // Merging
                    if (singleRange.contains(r))
                        return;

                    if (r.contains(singleRange)) {
                        singleRange = r;
                        singleSequence = sequence;
                        return;
                    }

                    if (begin < singleRange.getLower())
                        singleSequence = sequence.getRange(0, singleRange.getLower() - begin).concatenate(singleSequence);
                    else
                        singleSequence = singleSequence.getRange(0, begin - singleRange.getLower()).concatenate(sequence);
                    singleRange = singleRange.tryMerge(r);

                    return;
                }
                map = new RangeMap<>();
                map.put(singleRange, singleSequence);
                singleRange = null;
                singleSequence = null;
            }

            Entry<Range, NucleotideSequence> intersectingSeq = map.findSingleIntersection(r);
            if (intersectingSeq == null) {
                map.put(new Range(begin, begin + sequence.size()), sequence);
            } else {
                // Checking
                Range intersection = intersectingSeq.getKey().intersection(r);
                NucleotideSequence intersectionSequence = get(intersection);

                if (!intersectionSequence.equals(sequence.getRange(intersection.move(-begin))))
                    throw new IllegalArgumentException();

                // Merging
                if (intersectingSeq.getKey().contains(r))
                    return;

                // Removing this entry form map
                map.remove(intersectingSeq.getKey());

                if (r.contains(intersectingSeq.getKey())) {
                    map.put(r, sequence);
                    return;
                }

                NucleotideSequence s;
                if (begin < intersectingSeq.getKey().getLower())
                    s = sequence.getRange(0, intersectingSeq.getKey().getLower() - begin).concatenate(intersectingSeq.getValue());
                else
                    s = intersectingSeq.getValue().getRange(0, begin - intersectingSeq.getKey().getLower()).concatenate(sequence);

                map.put(intersectingSeq.getKey().tryMerge(r), s);
            }
        }

        NucleotideSequence get(Range range) {
            if (singleRange != null)
                if (singleRange.contains(range))
                    return singleSequence.getRange(range.move(-singleRange.getLower()));
                else return null;
            Entry<Range, NucleotideSequence> entry = map.findContaining(range);
            if (entry == null)
                return null;
            return entry.getValue().getRange(range.move(-entry.getKey().getLower()));
        }

        Range getAvailableRange(Range range) {
            if (singleRange != null)
                if (singleRange.contains(range))
                    return singleRange;
                else return null;
            Entry<Range, NucleotideSequence> entry = map.findContaining(range);
            if (entry == null)
                return null;
            return entry.getKey();
        }
    }
}
