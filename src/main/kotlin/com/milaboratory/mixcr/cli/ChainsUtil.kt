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
package com.milaboratory.mixcr.cli

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.forEach
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCObject
import io.repseq.core.Chains
import io.repseq.core.VDJCLibraryRegistry
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object ChainsUtil {
    /**
     * Extract all distinct chains from list of VDJCObject ports
     */
    fun <T : VDJCObject> allChains(ports: List<OutputPort<T>>) = run {
        val result = mutableSetOf<Chains>()
        for (port in ports) {
            port.use {
                port.forEach { result += it.commonTopChains() }
            }
        }
        result
    }

    /**
     * Extract all distinct chains from list of clnx files
     */
    fun allChainsFromClnx(files: List<Path>) = run {
        val chains = mutableSetOf<Chains>()
        for (reader in files.map { CloneSetIO.mkReader(it, VDJCLibraryRegistry.getDefault()) }) {
            reader.use {
                reader.readClones().forEach {
                    chains += it.commonTopChains()
                }
            }
        }
        chains
    }

    /**
     * Extract all distinct chains from list of vdjca files
     */
    fun allChainsFromVDJCA(files: List<Path>) = run {
        val chains = mutableSetOf<Chains>()
        for (reader in files.map { VDJCAlignmentsReader(it, VDJCLibraryRegistry.getDefault()) }) {
            reader.use {
                reader.forEach {
                    chains += it.commonTopChains()
                }
            }
        }
        chains
    }

    val Chains.name: String
        get() = if (isEmpty)
            "chimeras"
        else if (equals(Chains.TRAD))
            "TRAD"
        else
            this.toString()

    fun Chains.toPath(pattern: String, ext: String? = null) = toPath(Path(pattern), ext)

    fun Chains.toPath(pattern: Path, ext: String? = null) = run {
        val path = pattern.toAbsolutePath()
        val fname = path.nameWithoutExtension
        val fext = ext ?: pattern.extension
        path.parent.resolve(Path("$fname.$name.$fext"))
    }
}
