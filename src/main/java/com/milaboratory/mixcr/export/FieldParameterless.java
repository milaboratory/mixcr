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

public abstract class FieldParameterless<T> extends AbstractField<T> {
    final String hHeader, sHeader;

    protected FieldParameterless(Class targetType, String command,
                                 String description,
                                 String hHeader, String sHeader) {
        super(targetType, command, description);
        this.hHeader = hHeader;
        this.sHeader = sHeader;
    }

    @Override
    public int nArguments() {
        return 0;
    }

    protected abstract String extract(T object);

    public String getHeader(OutputMode outputMode) {
        switch (outputMode) {
            case HumanFriendly:
                return hHeader;
            case ScriptingFriendly:
                return sHeader;
            default:
                throw new NullPointerException();
        }
    }

    @Override
    public FieldExtractor<T> create(OutputMode outputMode, VDJCFileHeaderData headerData, String[] args) {
        return new AbstractFieldExtractor<T>(getHeader(outputMode), this) {
            @Override
            public String extractValue(T object) {
                return extract(object);
            }
        };
    }

    @Override
    public String metaVars() {
        return "";
    }
}
