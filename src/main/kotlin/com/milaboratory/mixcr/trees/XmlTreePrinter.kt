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

/**
 * https://en.wikipedia.org/wiki/Newick_format
 */
class XmlTreePrinter<T : Any>(
    private val nameExtractor: (T) -> String
) : TreePrinter<T> {
    override fun print(tree: Tree<out T>): String = printNode(tree.root, null)

    private fun printNode(
        node: Tree.Node<out T>,
        distanceFromParent: Double?
    ): String {

        val sb = StringBuilder()
        sb.append("<node content='")
        sb.append(nameExtractor(node.content))
        sb.append("'")
        if (distanceFromParent != null) {
            sb.append(" distance='")
            sb.append(distanceFromParent)
            sb.append("'")
        }
        if (node.links.isNotEmpty()) {
            sb.append(">")
            sb.append(
                node.links
                    .sortedBy { link -> link.distance }
                    .joinToString("") { link -> printNode(link.node, link.distance) }
            )
            sb.append("</node>")
        } else {
            sb.append("/>")
        }
        return sb.toString()
    }
}
