/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

public class CommonDescriptions {
    private CommonDescriptions() {
    }

    public static final String SPECIES =
            "Species (organism), as specified in library file or taxon id.%n" +
                    "Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, 9606, 10090 etc.";

    public static final String REPORT = "Report file (human readable version, see -j / --json-report for machine readable report)";

    public static final String JSON_REPORT = "JSON formatted report file";

}
