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
package com.milaboratory.mixcr.vdjaligners;

import com.fasterxml.jackson.core.type.TypeReference;
import com.milaboratory.util.GlobalObjectMappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class VDJCParametersPresets {
    private VDJCParametersPresets() {
    }

    private static Map<String, VDJCAlignerParameters> knownParameters;

    private static void ensureInitialized() {
        if (knownParameters == null)
            synchronized (VDJCParametersPresets.class) {
                if (knownParameters == null) {
                    Map<String, VDJCAlignerParameters> map;
                    try {
                        InputStream is = VDJCAlignerParameters.class.getClassLoader().getResourceAsStream("parameters/vdjcaligner_parameters.json");
                        TypeReference<HashMap<String, VDJCAlignerParameters>> typeRef = new TypeReference<HashMap<String, VDJCAlignerParameters>>() {};
                        map = GlobalObjectMappers.getOneLine().readValue(is, typeRef);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                    knownParameters = map;
                }
            }
    }

    public static Set<String> getAvailableParameterNames() {
        ensureInitialized();
        return knownParameters.keySet();
    }

    public static VDJCAlignerParameters getByName(String name) {
        ensureInitialized();
        VDJCAlignerParameters params;

        params = knownParameters.get(name);
        if (params != null)
            return params.clone();

        params = knownParameters.get(name.toLowerCase());
        if (params != null)
            return params.clone();

        return null;
    }
}
