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
package com.milaboratory.mixcr.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;

public class PrintStreamTableAdapter implements AutoCloseable {
    private PrintStream innerPrintStream;
    private boolean rowStarted = false;

    public PrintStreamTableAdapter(OutputStream stream) {
        this.innerPrintStream = new PrintStream(stream);
    }

    public PrintStreamTableAdapter(PrintStream innerPrintStream) {
        this.innerPrintStream = innerPrintStream;
    }

    public PrintStreamTableAdapter(String fileName) throws FileNotFoundException {
        this(new PrintStream(fileName));
    }

    public PrintStreamTableAdapter(File file) throws FileNotFoundException {
        this(new PrintStream(file));
    }

    public void row(String... cells) {
        cells(cells);
        newRow();
    }

    public void row(Object... cells) {
        cells(cells);
        newRow();
    }

    public void cells(Object... cells) {
        for (Object cell : cells)
            cell(cell);
    }

    public void cells(String... cells) {
        for (String cell : cells)
            cell(cell);
    }

    public void cellsFromArray(Object cells) {
        if (!cells.getClass().isArray())
            throw new IllegalArgumentException();
        int length = Array.getLength(cells);
        for (int i = 0; i < length; ++i)
            cell(Array.get(cells, i));
    }

    public void cell(String cell) {
        if (rowStarted)
            getInnerPrintStream().print('\t');
        else
            rowStarted = true;
        getInnerPrintStream().print(cell);
    }

    public void cell(Object cell) {
        if (cell == null)
            cell("");
        else
            cell(cell.toString());
    }

    public void newRow() {
        rowStarted = false;
        getInnerPrintStream().println();
    }

    @Override
    public void close() {
        innerPrintStream.close();
    }

    public PrintStream getInnerPrintStream() {
        return innerPrintStream;
    }
}
