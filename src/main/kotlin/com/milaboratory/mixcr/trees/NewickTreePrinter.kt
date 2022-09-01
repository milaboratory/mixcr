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

import java.util.stream.Collectors

/**
 * https://en.wikipedia.org/wiki/Newick_format
 */
class NewickTreePrinter<T : Any>(
    private val printDistances: Boolean = true,
    private val printOnlyLeafNames: Boolean = false,
    private val nameExtractor: (Tree.Node<out T>) -> String
) : TreePrinter<T> {
    override fun print(tree: Tree<out T>): String = printNode(tree.root) + ";"

    private fun printNode(node: Tree.Node<out T>): String {
        val sb = StringBuilder()
        if (node.links.isNotEmpty()) {
            sb.append(
                node.links.stream()
                    .map { link ->
                        val printedNode = printNode(link.node)
                        if (printDistances) {
                            return@map printedNode + ":" + link.distance
                        } else {
                            return@map printedNode
                        }
                }
                .sorted()
                .collect(Collectors.joining(",", "(", ")")))
        }
        if (!printOnlyLeafNames || node.links.isEmpty()) {
            sb.append(nameExtractor(node))
        }
        return sb.toString()
    }
}
