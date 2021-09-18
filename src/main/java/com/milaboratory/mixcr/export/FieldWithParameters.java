/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.export;

public abstract class FieldWithParameters<T, P> extends AbstractField<T> {
    final int nArguments;

    public FieldWithParameters(Class targetType, String command, String description, int nArguments) {
        super(targetType, command, description);
        this.nArguments = nArguments;
    }

    @Override
    public int nArguments() {
        return nArguments;
    }

    protected abstract P getParameters(String[] string);

    protected abstract String getHeader(OutputMode outputMode, P parameters);

    protected abstract String extractValue(T object, P parameters);

    @Override
    public FieldExtractor<T> create(OutputMode outputMode, String[] args) {
        final P params = getParameters(args);
        String header = getHeader(outputMode, params);
        return new AbstractFieldExtractor<T>(header, this) {
            @Override
            public String extractValue(T object) {
                return FieldWithParameters.this.extractValue(object, params);
            }
        };
    }
}
