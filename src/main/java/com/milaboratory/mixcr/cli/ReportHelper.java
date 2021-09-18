/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

public final class ReportHelper {
    private final PrintStream printStream;
    private final boolean stdout;

    public ReportHelper(String fileName) {
        try {
            this.printStream = new PrintStream(fileName);
            this.stdout = false;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public ReportHelper(OutputStream outputStream, boolean stdout) {
        this.printStream = new PrintStream(outputStream);
        this.stdout = stdout;
    }

    public ReportHelper(PrintStream printStream, boolean stdout) {
        this.printStream = printStream;
        this.stdout = stdout;
    }

    public boolean isStdout() {
        return stdout;
    }

    public ReportHelper writeField(String fieldName, Object value) {
        printStream.println(fieldName + ": " + value);
        return this;
    }

    public ReportHelper writePercentField(String fieldName, long value, long total) {
        double percent = 100.0 * value / total;
        printStream.println(fieldName + ": " + Util.PERCENT_FORMAT.format(percent) + "%");
        return this;
    }

    public ReportHelper writePercentAndAbsoluteField(String fieldName, long value, long total) {
        double percent = 100.0 * value / total;
        printStream.println(fieldName + ": " + value + " (" + Util.PERCENT_FORMAT.format(percent) + "%)");
        return this;
    }

    public ReportHelper writePercentAndAbsoluteField(String fieldName, double value, double total) {
        double percent = 100.0 * value / total;
        printStream.println(fieldName + ": " + value + " (" + Util.PERCENT_FORMAT.format(percent) + "%)");
        return this;
    }

    public ReportHelper writePercentField(String fieldName, AtomicLong value, long total) {
        writePercentField(fieldName, value.get(), total);
        return this;
    }

    public ReportHelper writePercentAndAbsoluteField(String fieldName, AtomicLong value, long total) {
        writePercentAndAbsoluteField(fieldName, value.get(), total);
        return this;
    }

    public ReportHelper end() {
        printStream.println("======================================");
        return this;
    }
}
