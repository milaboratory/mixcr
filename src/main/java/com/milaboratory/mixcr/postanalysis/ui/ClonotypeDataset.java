package com.milaboratory.mixcr.postanalysis.ui;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 */
public class ClonotypeDataset implements Dataset<Clone> {
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

    @Override
    public OutputPortCloseable<Clone> mkElementsPort() {
        try {
            return CloneSetIO.mkReader(path, registry, concurrencyLimiter).readClones();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
