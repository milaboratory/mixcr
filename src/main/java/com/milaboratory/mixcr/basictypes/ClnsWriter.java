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

import cc.redberry.pipe.InputPort;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.mixcr.cli.MiXCRCommandReport;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public final class ClnsWriter implements AutoCloseable {
    static final String MAGIC_V13 = "MiXCR.CLNS.V13";
    static final String MAGIC = MAGIC_V13;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);
    /**
     * Number of bytes in footer with meta information
     */
    static final int FOOTER_LENGTH = 8 + IOUtil.END_MAGIC_LENGTH;

    final PrimitivOHybrid output;

    public ClnsWriter(String fileName) throws IOException {
        this(new PrimitivOHybrid(Paths.get(fileName)));
    }

    public ClnsWriter(File file) throws IOException {
        this(new PrimitivOHybrid(file.toPath()));
    }

    public ClnsWriter(PrimitivOHybrid output) {
        this.output = output;
    }

    public void writeHeaderFromCloneSet(CloneSet cloneSet) {
        writeHeader(
                cloneSet.getInfo(),
                cloneSet.getOrdering(),
                cloneSet.getUsedGenes(),
                cloneSet.size()
        );
    }

    public void writeHeader(
            MiXCRMetaInfo info,
            VDJCSProperties.CloneOrdering ordering,
            List<VDJCGene> genes,
            int numberOfClones
    ) {
        try (PrimitivO o = output.beginPrimitivO(true)) {
            // Writing magic bytes
            o.write(MAGIC_BYTES);

            // Writing version information
            o.writeUTF(
                    MiXCRVersionInfo.get().getVersionString(
                            AppVersionInfo.OutputType.ToFile));

            // Writing analysis meta-information
            o.writeObject(info);
            o.writeObject(ordering);
            o.writeInt(numberOfClones);

            IOUtil.stdVDJCPrimitivOStateInit(o, genes, info.getAlignerParameters());
        }
    }

    /**
     * Must be closed by putting null
     */
    public InputPort<Clone> cloneWriter() {
        return output.beginPrimitivOBlocks(3, 512);
    }

    public void writeCloneSet(CloneSet cloneSet) {
        writeHeaderFromCloneSet(cloneSet);
        InputPort<Clone> cloneIP = cloneWriter();
        for (Clone clone : cloneSet)
            cloneIP.put(clone);
        cloneIP.put(null);
    }

    private List<MiXCRCommandReport> footer = null;

    /**
     * Write reports chain
     */
    public void writeFooter(List<MiXCRCommandReport> reports, MiXCRCommandReport report) {
        if (footer != null)
            throw new IllegalStateException("Footer already written");
        this.footer = new ArrayList<>();
        if (reports != null)
            footer.addAll(reports);
        if (report != null)
            footer.add(report);
    }

    @Override
    public void close() throws IOException {
        if (footer == null)
            throw new IllegalStateException("Footer not written");

        // position of reports
        long footerStartPosition = output.getPosition();

        try (PrimitivO o = output.beginPrimitivO()) {
            o.writeInt(footer.size());
            for (MiXCRCommandReport report : footer) {
                o.writeObject(report);
            }

            // Total size = 8 + END_MAGIC_LENGTH
            o.writeLong(footerStartPosition);
            // Writing end-magic as a file integrity sign
            o.write(IOUtil.getEndMagicBytes());
        }
        output.close();
    }
}
