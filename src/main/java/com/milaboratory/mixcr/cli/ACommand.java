package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.IOUtil;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class ACommand extends ABaseCommand implements Runnable {
    /** queue of warning messages */
    private List<String> warningsQueue = new ArrayList<>();
    /** flag that signals we are entered the run method */
    private boolean running;

    @Option(names = {"-nw", "--no-warnings"},
            description = "suppress all warning messages")
    public boolean quiet = false;

    @Option(description = "Verbose warning messages.",
            names = {"--verbose"})
    public boolean verbose = false;

    /** Warning message */
    public void warn(String message) {
        if (quiet)
            return;

        if (!running)
            // add to a queue
            warningsQueue.add(message);
        else
            // print immediately
            printWarn(message);
    }

    private void printWarn(String message) {
        if (!quiet)
            System.err.println(message);
    }

    /** list of intput files */
    protected List<String> getInputFiles() {return Collections.emptyList();}

    /** list of output files produces as result */
    protected List<String> getOutputFiles() {return Collections.emptyList();}

    /** Validate injected parameters and options */
    public void validate() {
        for (String in : getInputFiles()) {
            if (!new File(in).exists())
                throwValidationException("ERROR: input file \"" + in + "\" does not exist.", false);
            IOUtil.MiXCRFileInfo info = IOUtil.getFileInfo(in);
            if (info != null && !info.valid)
                throwValidationException("ERROR: input file \"" + in + "\" is corrupted.", false);
        }
    }

    @Override
    public final void run() {
        validate();
        if (!quiet)
            for (String m : warningsQueue)
                printWarn(m);


        running = true;
        try {
            run0();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Do actual job */
    public abstract void run0() throws Exception;
}
