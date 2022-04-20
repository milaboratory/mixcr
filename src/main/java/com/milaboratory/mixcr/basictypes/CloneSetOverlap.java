/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.sorting.MergeStrategy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class CloneSetOverlap {
    private CloneSetOverlap() {
    }

    public static OutputPortWithProgress<List<List<Clone>>> overlap(
            List<? extends VDJCSProperties.VDJCSProperty<? super Clone>> by,
            List<? extends CloneReader> readers) {
        VDJCSProperties.CloneOrdering ordering = readers.get(0).ordering();
        for (int i = 1; i < readers.size(); i++)
            if (!ordering.equals(readers.get(i).ordering()))
                throw new RuntimeException("All clonesets must be sorted the same way.");
        MergeStrategy<Clone> strategy = MergeStrategy.calculateStrategy(ordering.getProperties(), by);
        if (!strategy.usesStreamOrdering())
            throw new RuntimeException("Clone sorting is incompatible with overlap criteria.");

        List<OutputPortWithProgress<Clone>> individualPorts = readers
                .stream()
                .map(r -> OutputPortWithProgress.wrap(r.numberOfClones(), r.readClones()))
                .collect(Collectors.toList());

        OutputPortCloseable<List<List<Clone>>> joinedPort = strategy.join(individualPorts);

        AtomicLong index = new AtomicLong(0);
        AtomicBoolean isFinished = new AtomicBoolean(false);
        long totalClones = readers.stream().mapToLong(CloneReader::numberOfClones).sum();
        return new OutputPortWithProgress<List<List<Clone>>>() {
            @Override
            public long index() {
                return index.get();
            }

            @Override
            public void close() {
                joinedPort.close();
            }

            @Override
            public List<List<Clone>> take() {
                List<List<Clone>> t = joinedPort.take();
                if (t == null) {
                    isFinished.set(true);
                    return null;
                }
                index.incrementAndGet();
                return t;
            }

            @Override
            public double getProgress() {
                return 1.0 * individualPorts.stream().mapToLong(OutputPortWithProgress::index).sum() / totalClones;
            }

            @Override
            public boolean isFinished() {
                return isFinished.get();
            }
        };
    }
}
