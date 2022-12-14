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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPort;
import gnu.trove.map.hash.TIntIntHashMap;

public final class AssembledReadsPort implements OutputPort<ReadToCloneMapping> {
    final OutputPort<AssemblerEvent> initialEvents, mappingEvents;
    final TIntIntHashMap idMapping;
    final TIntIntHashMap preClustered;

    public AssembledReadsPort(OutputPort<AssemblerEvent> initialEvents,
                              OutputPort<AssemblerEvent> mappingEvents,
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

        assert eventMapping == null || eventMapping.preCloneIndex == event.preCloneIndex;

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
            return new ReadToCloneMapping(event.preCloneIndex, cloneIndex, false, false, false, false);

        boolean preCl = false;
        if (preClustered.containsKey(cloneIndex)) {
            preCl = true;
            cloneIndex = preClustered.get(cloneIndex);
        }

        if (!idMapping.containsKey(cloneIndex))
            return new ReadToCloneMapping(event.preCloneIndex, Integer.MIN_VALUE, false, false, true, preCl);

        cloneIndex = idMapping.get(cloneIndex);

        boolean clustered = false;
        if (cloneIndex < 0) {
            clustered = true;
            cloneIndex = -1 - cloneIndex;
        }

        return new ReadToCloneMapping(event.preCloneIndex, cloneIndex, clustered, mapped, false, preCl);
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
