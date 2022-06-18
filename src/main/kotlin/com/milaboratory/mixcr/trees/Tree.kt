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

import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import java.math.BigDecimal
import java.util.function.Consumer

/**
 *
 */
class Tree<T : Any>(
    val root: Node<T>
) {
    fun copy(): Tree<T> {
        return Tree(root.copy())
    }

    fun allNodes(): Sequence<NodeWithParent<T>> = sequenceOf(NodeWithParent(null, root, null)) + root.allDescendants()

    fun <R : Any> map(mapper: (T?, T, BigDecimal?) -> R): Tree<R> {
        return Tree(root.map(null, null, mapper))
    }

    class Node<T> {
        val content: T
        private val children: MutableList<NodeLink<T>>

        constructor(content: T) {
            this.content = content
            children = ArrayList()
        }

        constructor(content: T, children: List<NodeLink<T>>) {
            this.content = content
            this.children = children.toMutableList()
        }

        fun copy(): Node<T> {
            val childrenCopy = children.map { NodeLink(it.node.copy(), it.distance) }
            return Node(content, childrenCopy)
        }

        val links: List<NodeLink<T>>
            get() = children

        fun addChild(node: Node<T>, distance: BigDecimal): Node<T> {
            children.add(NodeLink(node, distance))
            return this
        }

        fun replaceChild(what: Node<T>, substitution: Node<T>, distance: BigDecimal) {
            require(children.removeIf { it.node === what })
            children.add(NodeLink(substitution, distance))
        }

        fun allDescendants(): Sequence<NodeWithParent<T>> = children.asSequence().flatMap { link ->
            sequenceOf(NodeWithParent(this, link.node, link.distance)) + link.node.allDescendants()
        }

        fun <R> map(parent: T?, distance: BigDecimal?, mapper: (T?, T, BigDecimal?) -> R): Node<R> {
            val mappedNode = Node(mapper(parent, content, distance))
            children.forEach(Consumer { child: NodeLink<T> ->
                mappedNode.addChild(
                    child.node.map(content, child.distance, mapper),
                    child.distance
                )
            })
            return mappedNode
        }
    }

    class NodeLink<T>(
        val node: Node<T>,
        val distance: BigDecimal
    )

    data class NodeWithParent<T>(
        val parent: Node<T>?,
        val node: Node<T>,
        val distance: BigDecimal?
    )
}

object TreeSerializer {
    fun writeTree(output: PrimitivO, obj: Tree<*>) {
        writeNode(output, obj.root)
    }

    private fun writeNode(output: PrimitivO, node: Tree.Node<*>) {
        output.writeObject(node.content)
        output.writeInt(node.links.size)
        node.links.forEach { link ->
            output.writeDouble(link.distance.toDouble())
            writeNode(output, link.node)
        }
    }

    inline fun <reified T : Any> readTree(input: PrimitivI): Tree<T> {
        return Tree(readNode(input, T::class.java))
    }

    fun <T> readNode(input: PrimitivI, klass: Class<T>): Tree.Node<T> {
        val content = input.readObject(klass)
        val count = input.readInt()
        val links = (0 until count).map {
            val distance = input.readDouble()
            val child = readNode(input, klass)
            Tree.NodeLink(child, BigDecimal.valueOf(distance))
        }
        return Tree.Node(content, links)
    }
}
