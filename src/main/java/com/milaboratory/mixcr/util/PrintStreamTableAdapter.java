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
