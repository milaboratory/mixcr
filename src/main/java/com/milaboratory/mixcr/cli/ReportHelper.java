/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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
