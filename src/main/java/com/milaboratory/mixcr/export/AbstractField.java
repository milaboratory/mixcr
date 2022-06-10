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
