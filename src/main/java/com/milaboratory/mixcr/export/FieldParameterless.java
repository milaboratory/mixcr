/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.export;

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
    public FieldExtractor<T> create(OutputMode outputMode, String[] args) {
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
