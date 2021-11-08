/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.postanalysis.stat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class HolmBonferroni {
    /**
     * Performs Holm-Bonferroni method.
     *
     * https://en.wikipedia.org/wiki/Holm%E2%80%93Bonferroni_method
     *
     * @param input           ALL result of multiple comparisons
     * @param pValueExtractor function to extract P-value from input objects
     * @param fwer            family-wise error rate, e.g. 0.01 (https://en.wikipedia.org/wiki/Family-wise_error_rate)
     * @param <T>             type of the object representing single comparison result
     * @return list of significant p-values, sorted from lowest to highest (FWER controlled)
     */
    public static <T> List<T> run(Collection<T> input,
                                  ToDoubleFunction<T> pValueExtractor,
                                  double fwer) {
        ArrayList<T> res = new ArrayList<>(input);
        res.sort(Comparator.comparingDouble(pValueExtractor));
        int i = 0;
        for (; i < res.size(); i++)
            if (pValueExtractor.applyAsDouble(res.get(i)) > fwer / (res.size() - i))
                break;
        return new ArrayList<>(res.subList(0, i));
    }
}
