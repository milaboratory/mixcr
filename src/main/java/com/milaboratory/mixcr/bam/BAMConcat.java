package com.milaboratory.mixcr.bam;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;

import java.util.ArrayList;

public class BAMConcat implements OutputPort<SAMRecord> {
    private final ArrayList<OutputPort<SAMRecord>> outputports;
    int portIdx = 0;

    public BAMConcat(SamReader[] reader) {
        outputports = new ArrayList<>();
        for (SamReader samRecords : reader) {
            outputports.add(CUtils.asOutputPort(samRecords));
        }
    }

    @Override
    public synchronized SAMRecord take() {
        while (portIdx < outputports.size()) {
            SAMRecord out = outputports.get(portIdx).take();
            if (out != null) {
                return out;
            } else {
                portIdx++;
            }
        }
        return null;
    }
}
