/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.export;

public interface Field<T> {
    boolean canExtractFrom(Class type);

    String getCommand();

    String getDescription();

    int nArguments();

    String metaVars();

    FieldExtractor<T> create(OutputMode outputMode, String[] args);
}
