package com.milaboratory.mixcr.postanalysis.ui;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import io.repseq.core.VDJCLibraryRegistry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 */
public class ClonesIterable implements Iterable<Clone>, AutoCloseable {
    final Path path;
    final VDJCLibraryRegistry registry;

    public ClonesIterable(Path path, VDJCLibraryRegistry registry) {
        this.path = path;
        this.registry = registry;
    }

    public ClonesIterable(String path, VDJCLibraryRegistry registry) {
        this(Paths.get(path), registry);
    }

    private final List<AutoCloseable> close = new ArrayList<>();

    @NotNull
    @Override
    public Iterator<Clone> iterator() {
        try {
            OutputPortCloseable<Clone> c = CloneSetIO.mkReader(path, registry).readClones();
            close.add(c);
            return CUtils.it(c).iterator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        Exception ex = null;
        for (AutoCloseable c : close)
            try {
                c.close();
            } catch (Exception e) {
                ex = e;
            }
        if (ex != null)
            throw ex;
    }
}
