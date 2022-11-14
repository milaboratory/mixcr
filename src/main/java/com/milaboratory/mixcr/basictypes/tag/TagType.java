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
package com.milaboratory.mixcr.basictypes.tag;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum TagType {
    Sample("SPL", "SAMPLE"),
    Cell("CELL"),
    Molecule("UMI", "MIG");

    public final List<String> aliases;

    TagType(String... aliases) {
        this.aliases = Collections.unmodifiableList(Arrays.asList(aliases));
    }
}
