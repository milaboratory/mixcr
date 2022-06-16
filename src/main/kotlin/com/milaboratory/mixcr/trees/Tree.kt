/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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

    fun <R : Any> map(mapper: (T?, T) -> R): Tree<R> {
        return Tree(root.map(null, mapper))
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

        fun <R> map(parent: T?, mapper: (T?, T) -> R): Node<R> {
            val mappedNode = Node(mapper(parent, content))
            children.forEach(Consumer { child: NodeLink<T> ->
                mappedNode.addChild(
                    child.node.map(content, mapper),
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
