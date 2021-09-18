/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.export;

public abstract class FieldExtractorWithParameters<T, P> extends AbstractFieldExtractor<T> {
    protected final P parameters;

    protected FieldExtractorWithParameters(String header, Field<T> descriptor, P parameters) {
        super(header, descriptor);
        this.parameters = parameters;
    }
}
