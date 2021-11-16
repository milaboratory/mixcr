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

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * https://en.wikipedia.org/wiki/Newick_format
 */
public class NewickTreePrinter<T, E> implements TreePrinter<T, E> {
    private final Function<T, String> nameExtractorFromReal;
    private final Function<E, String> nameExtractorFromSynthetic;
    private final boolean printDistances;
    private final boolean printOnlyLeafNames;

    public NewickTreePrinter(
            Function<T, String> nameExtractorFromReal,
            Function<E, String> nameExtractorFromSynthetic,
            boolean printDistances,
            boolean printOnlyLeafNames
    ) {
        this.nameExtractorFromReal = nameExtractorFromReal;
        this.nameExtractorFromSynthetic = nameExtractorFromSynthetic;
        this.printDistances = printDistances;
        this.printOnlyLeafNames = printOnlyLeafNames;
    }

    @Override
    public String print(Tree<T, E> tree) {
        return printNode(tree.getRoot()) + ";";
    }

    private String printNode(Tree.Node<T, E> node) {
        StringBuilder sb = new StringBuilder();
        if (!node.getLinks().isEmpty()) {
            sb.append(node.getLinks().stream()
                    .map(link -> {
                        String printedNode = printNode(link.getNode());
                        if (printDistances && link.getDistance() != null) {
                            return printedNode + ":" + link.getDistance();
                        } else {
                            return printedNode;
                        }
                    })
                    .collect(Collectors.joining(",", "(", ")")));
        }
        if (!printOnlyLeafNames || node.getLinks().isEmpty()) {
            if (node instanceof Tree.Node.Real<?, ?>) {
                sb.append(nameExtractorFromReal.apply(((Tree.Node.Real<T, E>) node).getContent()));
            } else if (node instanceof Tree.Node.Synthetic<?, ?>) {
                sb.append(nameExtractorFromSynthetic.apply(((Tree.Node.Synthetic<T, E>) node).getContent()));
            } else {
                throw new IllegalArgumentException();
            }
        }
        return sb.toString();
    }
}
