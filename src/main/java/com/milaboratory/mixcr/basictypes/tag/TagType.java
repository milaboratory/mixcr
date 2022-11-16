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

import java.util.*;

import static com.milaboratory.mixcr.basictypes.tag.TagsInfo.*;

public enum TagType {
    Sample("SPL", "SAMPLE", ALL_TAGS_OF_TYPE + "Sample"),
    Cell("CELL", ALL_TAGS_OF_TYPE + "Cell"),
    Molecule("UMI", "MIG", ALL_TAGS_OF_TYPE + "Molecule");

    public final List<String> aliases;

    TagType(String... aliases) {
        this.aliases = Collections.unmodifiableList(Arrays.asList(aliases));
    }

    private static final Map<String, TagType> caseInsensitiveMap;

    static {
        caseInsensitiveMap = new HashMap<>();
        for (TagType value : values())
            caseInsensitiveMap.put(value.name().toLowerCase(), value);
    }

    public static TagType valueOfCaseInsensitiveOrNull(String str) {
        return caseInsensitiveMap.get(str.toLowerCase());
    }
}
