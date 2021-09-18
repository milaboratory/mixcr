/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.export;

public abstract class AbstractField<T> implements Field<T> {
    protected final Class targetType;
    protected final String command, description;

    protected AbstractField(Class targetType, String command,
                            String description) {
        this.targetType = targetType;
        this.command = command;
        this.description = description;
    }

    @Override
    public boolean canExtractFrom(Class type) {
        return targetType.isAssignableFrom(type);
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
