/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail,
 * Popov Aleksandr (here and after addressed as Inventors)
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

import java.io.*;
import java.util.*;

import static com.milaboratory.mixcr.cli.SerializerCompatibilityUtil.classToName;

public final class SerializerCompatibilityOutput implements DataOutput, AutoCloseable {
    final DataOutput output;

    public SerializerCompatibilityOutput(OutputStream output) {
        this.output = new DataOutputStream(output);
    }

    @Override
    public void write(int b) {
        try {
            output.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(byte[] b) {
        try {
            output.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) {
        try {
            output.write(b, off, len);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBoolean(boolean v) {
        try {
            output.writeBoolean(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeByte(int v) {
        try {
            output.writeByte(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeShort(int v) {
        try {
            output.writeShort(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeChar(int v) {
        try {
            output.writeChar(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeInt(int v) {
        try {
            output.writeInt(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeLong(long v) {
        try {
            output.writeLong(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeFloat(float v) {
        try {
            output.writeFloat(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeDouble(double v) {
        try {
            output.writeDouble(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBytes(String s) {
        try {
            output.writeBytes(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeChars(String s) {
        try {
            output.writeChars(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeUTF(String s) {
        String outputString = s;
        for (HashMap.Entry<String, String> replaceRule : classToName.entrySet())
            outputString = outputString.replace(replaceRule.getKey(), replaceRule.getValue());
        try {
            output.writeUTF(outputString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (output instanceof Closeable)
                ((Closeable) output).close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
