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
public class Tree<T> {
    private Node<T> root;

    public Tree(Node<T> root) {
        this.root = root;
    }

    public Node<T> getRoot() {
        return root;
    }

    void setRoot(Node<T> root) {
        this.root = root;
    }

    public Stream<Node<T>> allNodes() {
        return Stream.concat(
                Stream.of(root),
                root.allDescendants()
        );
    }

    public <R> Tree<R> map(Function<T, R> mapper) {
        return new Tree<>(root.map(mapper));
    }

    public static class Node<T> {
        private final T content;
        private final List<NodeLink<T>> children;
        @Nullable
        private Node<T> parent = null;
        @Nullable
        private BigDecimal distanceFromParent = null;

        @Nullable
        Node<T> getParent() {
            return parent;
        }

        public Node<T> copy() {
            Node<T> node = new Node<>(content, children);
            node.setParent(parent, distanceFromParent);
            return node;
        }

        public BigDecimal sumOfDistancesToDescendants() {
            return children.stream()
                    .map(link -> {
                        BigDecimal selfDistance = link.getDistance() != null ? link.getDistance() : BigDecimal.ZERO;
                        return selfDistance.add(link.getNode().sumOfDistancesToDescendants());
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Nullable
        public BigDecimal getDistanceFromParent() {
            return distanceFromParent;
        }

        void setParent(Node<T> parent, BigDecimal distanceFromParent) {
            this.parent = parent;
            this.distanceFromParent = distanceFromParent;
        }

        protected Node(T content) {
            this.content = content;
            children = new ArrayList<>();
        }

        protected Node(T content, List<NodeLink<T>> children) {
            this.content = content;
            this.children = children;
        }

        public List<NodeLink<T>> getLinks() {
            return children;
        }

        public Node<T> addChild(Node<T> node) {
            return addChild(node, null);
        }

        public Node<T> addChild(Node<T> node, @Nullable BigDecimal distance) {
            node.setParent(this, distance);
            children.add(new NodeLink<>(node, distance));
            return this;
        }

        public T getContent() {
            return content;
        }

        public void replaceChild(Node<T> what, Node<T> substitution, @Nullable BigDecimal distance) {
            if (!children.removeIf(it -> it.node == what)) {
                throw new IllegalArgumentException();
            }
            children.add(new NodeLink<>(substitution, distance));
            substitution.setParent(this, distance);
        }

        Stream<Node<T>> allDescendants() {
            return children.stream()
                    .map(NodeLink::getNode)
                    .flatMap(child -> Stream.concat(Stream.of(child), child.allDescendants()));
        }

        public <R> Node<R> map(Function<T, R> mapper) {
            List<NodeLink<R>> mapperLinks = children.stream()
                    .map(child -> new NodeLink<>(child.node.map(mapper), child.distance))
                    .collect(Collectors.toList());
            return new Node<>(mapper.apply(content), mapperLinks);
        }
    }

    static class NodeLink<T> {
        private final Node<T> node;
        @Nullable
        private final BigDecimal distance;

        public NodeLink(Node<T> node, @Nullable BigDecimal distance) {
            this.node = node;
            this.distance = distance;
        }

        public Node<T> getNode() {
            return node;
        }

        @Nullable
        public BigDecimal getDistance() {
            return distance;
        }
    }
}
