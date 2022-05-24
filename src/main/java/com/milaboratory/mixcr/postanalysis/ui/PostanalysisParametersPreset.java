package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.util.GlobalObjectMappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PostanalysisParametersPreset<T> {
    private static volatile Map<String, PostanalysisParametersIndividual> knownParametersIndividual;
    private static volatile Map<String, PostanalysisParametersOverlap> knownParametersOverlap;

    private static void ensureInitialized() {
        if (knownParametersIndividual == null)
            synchronized (PostanalysisParametersPreset.class) {
                if (knownParametersIndividual == null) {
                    Map<String, PostanalysisParametersIndividual> map;
                    try {
                        InputStream is = CloneAssemblerParameters.class.getClassLoader().getResourceAsStream("parameters/postanalysis_individual.json");
                        TypeReference<HashMap<String, PostanalysisParametersIndividual>> typeRef
                                = new TypeReference<HashMap<String, PostanalysisParametersIndividual>>() {
                        };
                        map = GlobalObjectMappers.getOneLine().readValue(is, typeRef);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                    knownParametersIndividual = map;

                    Map<String, PostanalysisParametersOverlap> map2;
                    try {
                        InputStream is = CloneAssemblerParameters.class.getClassLoader().getResourceAsStream("parameters/postanalysis_overlap.json");
                        TypeReference<HashMap<String, PostanalysisParametersOverlap>> typeRef
                                = new TypeReference<HashMap<String, PostanalysisParametersOverlap>>() {
                        };
                        map2 = GlobalObjectMappers.getOneLine().readValue(is, typeRef);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                    knownParametersOverlap = map2;
                }
            }
    }

    public static PostanalysisParametersIndividual getByNameIndividual(String name) {
        ensureInitialized();
        return knownParametersIndividual.get(name);
    }

    public static PostanalysisParametersOverlap getByNameOverlap(String name) {
        ensureInitialized();
        return knownParametersOverlap.get(name);
    }
}
