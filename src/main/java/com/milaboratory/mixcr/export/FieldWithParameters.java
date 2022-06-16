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
package com.milaboratory.mixcr.export;

import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData;

public abstract class FieldWithParameters<T, P> extends AbstractField<T> {
    final int nArguments;

    public FieldWithParameters(Class<T> targetType, String command, String description, int nArguments) {
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
    public FieldExtractor<T> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
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
