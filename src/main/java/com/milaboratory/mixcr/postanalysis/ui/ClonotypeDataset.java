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
package com.milaboratory.mixcr.postanalysis.ui;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 *
 */
public class ClonotypeDataset implements Dataset<Clone>, CloneReader {
    final String id;
    final Path path;
    final VDJCLibraryRegistry registry;
    final LambdaSemaphore concurrencyLimiter;

    public ClonotypeDataset(String id, Path path, VDJCLibraryRegistry registry) {
        this(id, path, registry, new LambdaSemaphore(CloneSetIO.DEFAULT_READER_CONCURRENCY_LIMIT));
    }

    public ClonotypeDataset(String id, Path path, VDJCLibraryRegistry registry, LambdaSemaphore concurrencyLimiter) {
        this.id = id;
        this.path = path;
        this.registry = registry;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    public ClonotypeDataset(String id, String path, VDJCLibraryRegistry registry) {
        this(id, path, registry, new LambdaSemaphore(CloneSetIO.DEFAULT_READER_CONCURRENCY_LIMIT));
    }

    public ClonotypeDataset(String id, String path, VDJCLibraryRegistry registry, LambdaSemaphore concurrencyLimiter) {
        this(id, Paths.get(path), registry, concurrencyLimiter);
    }

    @Override
    public String id() {
        return id;
    }

    private volatile CloneReader reader;

    @Override
    public OutputPortWithProgress<Clone> mkElementsPort() {
        if (reader == null) {
            synchronized (this) {
                if (reader == null)
                    try {
                        reader = CloneSetIO.mkReader(path, registry, concurrencyLimiter);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            }
        }
        return OutputPortWithProgress.wrap(reader.numberOfClones(), reader.readClones());
    }

    @Override
    public VDJCSProperties.CloneOrdering ordering() {
        return reader.ordering();
    }

    @Override
    public OutputPort<Clone> readClones() {
        return mkElementsPort();
    }

    @Override
    public int numberOfClones() {
        return reader.numberOfClones();
    }

    @Override
    public List<VDJCGene> getUsedGenes() {
        return reader.getUsedGenes();
    }

    @Override
    public MiXCRHeader getHeader() {
        return reader.getHeader();
    }

    @Override
    public MiXCRFooter getFooter() {
        return reader.getFooter();
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
}
