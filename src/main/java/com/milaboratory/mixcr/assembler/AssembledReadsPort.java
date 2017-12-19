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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPortCloseable;
import gnu.trove.map.hash.TIntIntHashMap;

public final class AssembledReadsPort implements OutputPortCloseable<ReadToCloneMapping> {
    final OutputPortCloseable<AssemblerEvent> initialEvents, mappingEvents;
    final TIntIntHashMap idMapping;
    final TIntIntHashMap preClustered;

    public AssembledReadsPort(OutputPortCloseable<AssemblerEvent> initialEvents,
                              OutputPortCloseable<AssemblerEvent> mappingEvents,
                              TIntIntHashMap idMapping,
                              TIntIntHashMap preClustered) {
        this.initialEvents = initialEvents;
        this.mappingEvents = mappingEvents;
        this.idMapping = idMapping;
        this.preClustered = preClustered;
    }

    @Override
    public ReadToCloneMapping take() {
        AssemblerEvent event, eventMapping;

        synchronized (this) {
            event = initialEvents.take();
        }

        if (event == null)
            return null;

        if (mappingEvents != null)
            synchronized (this) {
                eventMapping = mappingEvents.take();
            }
        else
            eventMapping = null;

        assert eventMapping == null || eventMapping.alignmentsIndex == event.alignmentsIndex;

        int cloneIndex = event.cloneIndex;
        boolean mapped = false;
        if (eventMapping != null && (eventMapping.cloneIndex >= 0 || eventMapping.cloneIndex <= -4)) {
            cloneIndex = eventMapping.cloneIndex;
            if (cloneIndex < 0) {
                cloneIndex = -4 - cloneIndex;
                mapped = true;
            }
            assert event.cloneIndex == AssemblerEvent.DEFERRED;
            assert cloneIndex >= 0;
        }

        if (cloneIndex < 0)
            return new ReadToCloneMapping(event.alignmentsIndex, cloneIndex, false, false, false, false);

        boolean preCl = false;
        if (preClustered.containsKey(cloneIndex)) {
            preCl = true;
            cloneIndex = preClustered.get(cloneIndex);
        }

        if (!idMapping.containsKey(cloneIndex))
            return new ReadToCloneMapping(event.alignmentsIndex, Integer.MIN_VALUE, false, false, true, preCl);

        cloneIndex = idMapping.get(cloneIndex);

        boolean clustered = false;
        if (cloneIndex < 0) {
            clustered = true;
            cloneIndex = -1 - cloneIndex;
        }

        return new ReadToCloneMapping(event.alignmentsIndex, cloneIndex, clustered, mapped, false, preCl);
    }

    @Override
    public void close() {
        Throwable t = null;
        try {
            initialEvents.close();
        } catch (RuntimeException re) {
            t = re;
        }
        try {
            if (mappingEvents != null)
                mappingEvents.close();
        } catch (RuntimeException re) {
            if (t != null)
                t = re;
        }
        if (t != null)
            throw new RuntimeException(t);
    }

}
