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
package com.milaboratory.mixcr.trees;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class Tree<T, E> {
    private Node<T, E> root;

    public Tree(Node<T, E> root) {
        this.root = root;
    }

    public Node<T, E> getRoot() {
        return root;
    }

    void setRoot(Node<T, E> root) {
        this.root = root;
    }

    public Stream<Node<T, E>> allNodes() {
        return Stream.concat(
                Stream.of(root),
                root.allDescendants()
        );
    }

    public <R1, R2> Tree<R1, R2> map(Function<T, R1> mapperReal, Function<E, R2> mapperSynthetic) {
        return new Tree<>(root.map(mapperReal, mapperSynthetic));
    }

    public static abstract class Node<T, E> {
        private final List<NodeLink<T, E>> children;
        @Nullable
        private Node<T, E> parent = null;

        @Nullable
        Node<T, E> getParent() {
            return parent;
        }

        void setParent(Node<T, E> parent) {
            this.parent = parent;
        }

        protected Node() {
            children = new ArrayList<>();
        }

        protected Node(List<NodeLink<T, E>> children) {
            this.children = children;
        }

        public List<NodeLink<T, E>> getLinks() {
            return children;
        }

        public void addChild(Node<T, E> node, @Nullable BigDecimal distance) {
            node.setParent(this);
            children.add(new NodeLink<>(node, distance));
        }

        public void replaceChild(Node<T, E> what, Node<T, E> substitution, @Nullable BigDecimal distance) {
            if (!children.removeIf(it -> it.node == what)) {
                throw new IllegalArgumentException();
            }
            children.add(new NodeLink<>(substitution, distance));
        }

        Stream<Node<T, E>> allDescendants() {
            return children.stream()
                    .map(NodeLink::getNode)
                    .flatMap(child -> Stream.concat(Stream.of(child), child.allDescendants()));
        }

        public <R1, R2> Node<R1, R2> map(Function<T, R1> mapperReal, Function<E, R2> mapperSynthetic) {
            List<NodeLink<R1, R2>> mapperLinks = children.stream()
                    .map(child -> new NodeLink<>(child.node.map(mapperReal, mapperSynthetic), child.distance))
                    .collect(Collectors.toList());
            if (this instanceof Real) {
                return new Node.Real<>(mapperReal.apply(((Real<T, E>) this).getContent()), mapperLinks);
            } else if (this instanceof Synthetic) {
                return new Node.Synthetic<>(mapperSynthetic.apply(((Synthetic<T, E>) this).getContent()), mapperLinks);
            } else {
                throw new IllegalStateException();
            }
        }

        public static class Real<T, E> extends Node<T, E> {
            private final T content;

            public Real(T content) {
                this.content = content;
            }

            public Real(T content, List<NodeLink<T, E>> children) {
                super(children);
                this.content = content;
            }

            public T getContent() {
                return content;
            }
        }

        public static class Synthetic<T, E> extends Node<T, E> {
            private final E content;

            public Synthetic(E content) {
                this.content = content;
            }

            public Synthetic(E content, List<NodeLink<T, E>> children) {
                super(children);
                this.content = content;
            }

            public E getContent() {
                return content;
            }
        }
    }

    public static class NodeLink<T, E> {
        private final Node<T, E> node;
        @Nullable
        private final BigDecimal distance;

        public NodeLink(Node<T, E> node, @Nullable BigDecimal distance) {
            this.node = node;
            this.distance = distance;
        }

        public Node<T, E> getNode() {
            return node;
        }

        @Nullable
        public BigDecimal getDistance() {
            return distance;
        }
    }
}
