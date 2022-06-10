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
package com.milaboratory.mixcr.postanalysis.downsampling;

import org.apache.commons.math3.random.RandomGenerator;

import static com.milaboratory.mixcr.postanalysis.downsampling.RandomHypergeometric.random_hypergeometric;

/**
 * Adapted from nymphy: https://github.com/numpy/numpy/tree/master/numpy/random/src/distributions
 */
public class RandomMvhgMarginals {

    /**
     *  random_multivariate_hypergeometric_marginals
     *
     *  Draw samples from the multivariate hypergeometric distribution--
     *  the "marginals" algorithm.
     *
     *  This version generates the sample by iteratively calling
     *  hypergeometric() (the univariate hypergeometric distribution).
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
     *      The length of the `colors` array.  The functions assumes
     *      num_colors > 0.
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
     *  Here's an example that demonstrates the idea of this algorithm.
     *
     *  Suppose the urn contains red, green, blue and yellow marbles.
     *  Let nred be the number of red marbles, and define the quantities for
     *  the other colors similarly.  The total number of marbles is
     *
     *      total = nred + ngreen + nblue + nyellow.
     *
     *  To generate a sample using rk_hypergeometric:
     *
     *     red_sample = hypergeometric(ngood=nred, nbad=total - nred,
     *                                 nsample=nsample)
     *
     *  This gives us the number of red marbles in the sample.  The number of
     *  marbles in the sample that are *not* red is nsample - red_sample.
     *  To figure out the distribution of those marbles, we again use
     *  rk_hypergeometric:
     *
     *      green_sample = hypergeometric(ngood=ngreen,
     *                                    nbad=total - nred - ngreen,
     *                                    nsample=nsample - red_sample)
     *
     *  Similarly,
     *
     *      blue_sample = hypergeometric(
     *                        ngood=nblue,
     *                        nbad=total - nred - ngreen - nblue,
     *                        nsample=nsample - red_sample - green_sample)
     *
     *  Finally,
     *
     *      yellow_sample = total - (red_sample + green_sample + blue_sample).
     *
     *  The above sequence of steps is implemented as a loop for an arbitrary
     *  number of colors in the innermost loop in the code below.  `remaining`
     *  is the value passed to `nbad`; it is `total - colors[0]` in the first
     *  call to random_hypergeometric(), and then decreases by `colors[j]` in
     *  each iteration.  `num_to_sample` is the `nsample` argument.  It
     *  starts at this function's `nsample` input, and is decreased by the
     *  result of the call to random_hypergeometric() in each iteration.
     *
     *  Assumptions on the arguments (not checked in the function):
     *    *  colors[k] >= 0  for k in range(num_colors)
     *    *  total = sum(colors)
     *    *  0 <= nsample <= total
     *    *  the product num_variates * num_colors does not overflow
     */
    public static void random_multivariate_hypergeometric_marginals(RandomGenerator bitgen_state,
                                                      long total,
                                                      long[] colors,
                                                      long nsample,
                                                      long[] variates)
    {
        int num_colors = colors.length;
        int num_variates = 1;
        boolean more_than_half;

        if ((total == 0) || (nsample == 0) || (num_variates == 0)) {
            // Nothing to do.
            return;
        }

        more_than_half = nsample > (total / 2);
        if (more_than_half) {
            nsample = total - nsample;
        }

        for (int i = 0; i < num_variates * num_colors; i += num_colors) {
            long num_to_sample = nsample;
            long remaining = total;
            for (int j = 0; (num_to_sample > 0) && (j + 1 < num_colors); ++j) {
                long r;
                remaining -= colors[j];
                r = random_hypergeometric(bitgen_state,
                        colors[j], remaining, num_to_sample);
                variates[i + j] = r;
                num_to_sample -= r;
            }

            if (num_to_sample > 0) {
                variates[i + num_colors - 1] = num_to_sample;
            }

            if (more_than_half) {
                for (int k = 0; k < num_colors; ++k) {
                    variates[i + k] = colors[k] - variates[i + k];
                }
            }
        }
    }
}
