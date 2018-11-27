package com.milaboratory.mixcr.cli;

import java.io.*;
import java.util.*;

import static com.milaboratory.mixcr.cli.SerializerCompatibilityUtil.nameToClass;

public final class SerializerCompatibilityInput implements DataInput, AutoCloseable {
    final DataInput input;

    public SerializerCompatibilityInput(DataInput input) {
        this.input = input;
    }

    public SerializerCompatibilityInput(InputStream input) {
        this((DataInput) new DataInputStream(input));
    }

    @Override
    public void readFully(byte[] b) {
        try {
            input.readFully(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFully(byte[] b, int off, int len) {
        try {
            input.readFully(b, off, len);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int skipBytes(int n) {
        try {
            return input.skipBytes(n);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean readBoolean() {
        try {
            return input.readBoolean();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte readByte() {
        try {
            return input.readByte();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int readUnsignedByte() {
        try {
            return input.readUnsignedByte();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public short readShort() {
        try {
            return input.readShort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int readUnsignedShort() {
        try {
            return input.readUnsignedShort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public char readChar() {
        try {
            return input.readChar();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int readInt() {
        try {
            return input.readInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long readLong() {
        try {
            return input.readLong();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public float readFloat() {
        try {
            return input.readFloat();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double readDouble() {
        try {
            return input.readDouble();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() {
        try {
            String inputString = input.readUTF();
            for (HashMap.Entry<String, String> replaceRule : nameToClass.entrySet())
                inputString = inputString.replace(replaceRule.getKey(), replaceRule.getValue());
            return inputString;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (input instanceof Closeable)
                ((Closeable) input).close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
