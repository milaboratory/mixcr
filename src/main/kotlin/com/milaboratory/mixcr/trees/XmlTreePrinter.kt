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

import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import java.util.stream.Collectors

/**
 * https://en.wikipedia.org/wiki/Newick_format
 */
class XmlTreePrinter<T : Any>(
    private val nameExtractor: (NodeWithParent<T>) -> String
) : TreePrinter<T> {
    override fun print(tree: Tree<T>): String {
        return printNode(NodeWithParent(null, tree.root, null))
    }

    private fun printNode(nodeWithParent: NodeWithParent<T>): String {
        val node = nodeWithParent.node
        val sb = StringBuilder()
        sb.append("<node content='")
        sb.append(nameExtractor(nodeWithParent))
        sb.append("'")
        if (nodeWithParent.distance != null) {
            sb.append(" distance='")
            sb.append(nodeWithParent.distance)
            sb.append("'")
        }
        if (node.links.isNotEmpty()) {
            sb.append(">")
            sb.append(node.links.stream()
                .sorted(Comparator.comparing { link -> link.distance })
                .map { link -> printNode(NodeWithParent(node, link.node, link.distance)) }
                .sorted()
                .collect(Collectors.joining("")))
            sb.append("</node>")
        } else {
            sb.append("/>")
        }
        return sb.toString()
    }
}
