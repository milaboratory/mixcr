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
package com.milaboratory.mixcr.trees

enum class BuildSHMTreeStep(val forPrint: String) {
    /**
     * @see ClusterProcessor.buildTreeTopParts
     */
    BuildingInitialTrees("Building initial trees"),

    /**
     * @see ClusterProcessor.attachClonesByDistanceChange
     */
    AttachClonesByDistanceChange("Attaching clones by distance change"),

    /**
     * @see ClusterProcessor.combineTrees
     */
    CombineTrees("Combining trees"),

    /**
     * @see ClusterProcessor.attachClonesByNDN
     */
    AttachClonesByNDN("Attaching clones by NDN");
}
