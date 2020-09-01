/*
 * Copyright (c) 2005-2020, NumPy Developers.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *        copyright notice, this list of conditions and the following
 *        disclaimer in the documentation and/or other materials provided
 *        with the distribution.
 *
 *     * Neither the name of the NumPy Developers nor the names of any
 *        contributors may be used to endorse or promote products derived
 *        from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.milaboratory.mixcr.postanalysis.downsampling;

import org.apache.commons.math3.random.RandomGenerator;

import static com.milaboratory.mixcr.postanalysis.downsampling.RandomHypergeometric.random_interval;


/**
 * Adapted from nymphy: https://github.com/numpy/numpy/tree/master/numpy/random/src/distributions
 */
public class RandomMvhgCounts {


    /**
     *  random_multivariate_hypergeometric_count
     *
     *  Draw variates from the multivariate hypergeometric distribution--
     *  the "count" algorithm.
     *
     *  Parameters
     *  ----------
     *  bitgen_t *bitgen_state
     *      Pointer to a `bitgen_t` instance.
     *  long total
     *      The sum of the values in the array `colors`.  (This is redundant
     *      information, but we know the caller has already computed it, so
     *      we might as well use it.)
     *  size_t num_colors
     *      The length of the `colors` array.
     *  long *colors
     *      The array of colors (i.e. the number of each type in the collection
     *      from which the random variate is drawn).
     *  long nsample
     *      The number of objects drawn without replacement for each variate.
     *      `nsample` must not exceed sum(colors).  This condition is not checked;
     *      it is assumed that the caller has already validated the value.
     *  size_t num_variates
     *      The number of variates to be produced and put in the array
     *      pointed to by `variates`.  One variate is a vector of length
     *      `num_colors`, so the array pointed to by `variates` must have length
     *      `num_variates * num_colors`.
     *  long *variates
     *      The array that will hold the result.  It must have length
     *      `num_variates * num_colors`.
     *      The array is not initialized in the function; it is expected that the
     *      array has been initialized with zeros when the function is called.
     *
     *  Notes
     *  -----
     *  The "count" algorithm for drawing one variate is roughly equivalent to the
     *  following numpy code:
     *
     *      choices = np.repeat(np.arange(len(colors)), colors)
     *      selection = np.random.choice(choices, nsample, replace=False)
     *      variate = np.bincount(selection, minlength=len(colors))
     *
     *  This function uses a temporary array with length sum(colors).
     *
     *  Assumptions on the arguments (not checked in the function):
     *    *  colors[k] >= 0  for k in range(num_colors)
     *    *  total = sum(colors)
     *    *  0 <= nsample <= total
     *    *  the product total * sizeof(size_t) does not exceed SIZE_MAX
     *    *  the product num_variates * num_colors does not overflow
     */
    public static void random_multivariate_hypergeometric_count(RandomGenerator bitgen_state,
                                                 long total,
                                                 long[] colors,
                                                 long nsample,
                                                 long[] variates)
    {
        int num_colors = colors.length;
        int num_variates = 1;
        int[] choices;
        boolean more_than_half;

        if (total > Integer.MAX_VALUE)
            throw new IllegalArgumentException();

        if ((total == 0) || (nsample == 0) || (num_variates == 0)) {
            // Nothing to do.
            return;
        }

        choices = new int[(int) total];

        /*
         *  If colors contains, for example, [3 2 5], then choices
         *  will contain [0 0 0 1 1 2 2 2 2 2].
         */
        for (int i = 0, k = 0; i < num_colors; ++i) {
            for (long j = 0; j < colors[i]; ++j) {
                choices[k] = i;
                ++k;
            }
        }

        more_than_half = nsample > (total / 2);
        if (more_than_half) {
            nsample = total - nsample;
        }

        for (int i = 0; i < num_variates * num_colors; i += num_colors) {
            /*
             *  Fisher-Yates shuffle, but only loop through the first
             *  `nsample` entries of `choices`.  After the loop,
             *  choices[:nsample] contains a random sample from the
             *  the full array.
             */
            for (int j = 0; j < (int) nsample; ++j) {
                int tmp, k;
                // Note: nsample is not greater than total, so there is no danger
                // of integer underflow in `(size_t) total - j - 1`.
                k = j + (int) random_interval(bitgen_state,
                        (int) total - j - 1);
                tmp = choices[k];
                choices[k] = choices[j];
                choices[j] = tmp;
            }
            /*
             *  Count the number of occurrences of each value in choices[:nsample].
             *  The result, stored in sample[i:i+num_colors], is the sample from
             *  the multivariate hypergeometric distribution.
             */
            for (int j = 0; j < (int) nsample; ++j) {
                variates[i + choices[j]] += 1;
            }

            if (more_than_half) {
                for (int k = 0; k < num_colors; ++k) {
                    variates[i + k] = colors[k] - variates[i + k];
                }
            }
        }
    }
}
