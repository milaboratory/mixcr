/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.util.OutputPortWithExpectedSize;
import com.milaboratory.util.OutputPortWithExpectedSizeKt;
import io.repseq.core.VDJCGene;

import java.util.List;
import java.util.stream.Collectors;

/** Merges several clone readers */
public class CloneReaderMerger implements CloneReader {
    final List<CloneReader> readers;
    final CloneReader rep;

    public CloneReaderMerger(List<CloneReader> readers) {
        if (readers.isEmpty())
            throw new IllegalArgumentException("empty input");
        this.readers = readers;
        this.rep = readers.get(0);
    }

    @Override
    public VDJCSProperties.CloneOrdering ordering() {
        return rep.ordering();
    }

    @Override
    public OutputPortWithExpectedSize<Clone> readClones() {
        FlatteningOutputPort<Clone> combined = new FlatteningOutputPort<>(CUtils.asOutputPort(readers.stream().map(CloneReader::readClones).collect(Collectors.toList())));
        return OutputPortWithExpectedSizeKt.withExpectedSize(combined, readers.stream().mapToInt(CloneReader::numberOfClones).sum());
    }

    @Override
    public int numberOfClones() {
        return readers.stream().mapToInt(CloneReader::numberOfClones).sum();
    }

    @Override
    public List<VDJCGene> getUsedGenes() {
        return rep.getUsedGenes();
    }


    @Override
    public MiXCRHeader getHeader() {
        return rep.getHeader();
    }

    @Override
    public MiXCRFooter getFooter() {
        return rep.getFooter();
    }

    @Override
    public void close() throws Exception {
        Exception err = null;
        for (CloneReader r : readers) {
            try {
                r.close();

            } catch (Exception e) {
                if (err == null)
                    err = e;
            }
        }
        if (err != null)
            throw err;
    }
}
