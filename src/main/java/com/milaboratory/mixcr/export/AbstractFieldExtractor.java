/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.export;

public abstract class AbstractFieldExtractor<T> implements FieldExtractor<T> {
    protected final String header;
    protected final Field<T> descriptor;

    protected AbstractFieldExtractor(String header, Field<T> descriptor) {
        this.header = header;
        this.descriptor = descriptor;
    }

    @Override
    public final String getHeader() {
        return header;
    }
}
