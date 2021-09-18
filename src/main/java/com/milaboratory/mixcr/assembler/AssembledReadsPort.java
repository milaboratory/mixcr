/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
