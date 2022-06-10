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

import static com.milaboratory.mixcr.postanalysis.downsampling.LogFactorial.logfactorial;
import static java.lang.Math.floor;
import static java.lang.Math.log;

/**
 * Adapted from nymphy: https://github.com/numpy/numpy/tree/master/numpy/random/src/distributions
 */
public class RandomHypergeometric {

    static long random_interval(RandomGenerator bitgen_state, long max) {
        long mask, value;
        if (max == 0) {
            return 0;
        }

        mask = max;

        /* Smallest bit mask >= max */
        mask |= mask >> 1;
        mask |= mask >> 2;
        mask |= mask >> 4;
        mask |= mask >> 8;
        mask |= mask >> 16;
        mask |= mask >> 32;

        /* Search a random value in [0..mask] <= max */
        if (max <= 0xffffffffL) {
            while ((value = (bitgen_state.nextInt() & mask)) > max) ;
        } else {
            while ((value = (bitgen_state.nextInt() & mask)) > max) ;
        }
        return value;
    }

    /**
     *  Generate a sample from the hypergeometric distribution.
     *
     *  Assume sample is not greater than half the total.  See below
     *  for how the opposite case is handled.
     *
     *  We initialize the following:
     *      computed_sample = sample
     *      remaining_good = good
     *      remaining_total = good + bad
     *
     *  In the loop:
     *  * computed_sample counts down to 0;
     *  * remaining_good is the number of good choices not selected yet;
     *  * remaining_total is the total number of choices not selected yet.
     *
     *  In the loop, we select items by choosing a random integer in
     *  the interval [0, remaining_total), and if the value is less
     *  than remaining_good, it means we have selected a good one,
     *  so remaining_good is decremented.  Then, regardless of that
     *  result, computed_sample is decremented.  The loop continues
     *  until either computed_sample is 0, remaining_good is 0, or
     *  remaining_total == remaining_good.  In the latter case, it
     *  means there are only good choices left, so we can stop the
     *  loop early and select what is left of computed_sample from
     *  the good choices (i.e. decrease remaining_good by computed_sample).
     *
     *  When the loop exits, the actual number of good choices is
     *  good - remaining_good.
     *
     *  If sample is more than half the total, then initially we set
     *      computed_sample = total - sample
     *  and at the end we return remaining_good (i.e. the loop in effect
     *  selects the complement of the result).
     *
     *  It is assumed that when this function is called:
     *    * good, bad and sample are nonnegative;
     *    * the sum good+bad will not result in overflow;
     *    * sample <= good+bad.
     */
    public static long hypergeometric_sample(RandomGenerator bitgen_state,
                                      long good, long bad, long sample) {
        long remaining_total, remaining_good, result, computed_sample;
        long total = good + bad;

        if (sample > total / 2) {
            computed_sample = total - sample;
        } else {
            computed_sample = sample;
        }

        remaining_total = total;
        remaining_good = good;

        while ((computed_sample > 0) && (remaining_good > 0) &&
                (remaining_total > remaining_good)) {
            // random_interval(bitgen_state, max) returns an integer in
            // [0, max] *inclusive*, so we decrement remaining_total before
            // passing it to random_interval().
            --remaining_total;
            if ((long) random_interval(bitgen_state,
                    remaining_total) < remaining_good) {
                // Selected a "good" one, so decrement remaining_good.
                --remaining_good;
            }
            --computed_sample;
        }

        if (remaining_total == remaining_good) {
            // Only "good" choices are left.
            remaining_good -= computed_sample;
        }

        if (sample > total / 2) {
            result = remaining_good;
        } else {
            result = good - remaining_good;
        }

        return result;
    }


// D1 = 2*sqrt(2/e)
// D2 = 3 - 2*sqrt(3/e)

    public static final double D1 = 1.7155277699214135;
    public static final double D2 = 0.8989161620588988;

    /**
     *  Generate variates from the hypergeometric distribution
     *  using the ratio-of-uniforms method.
     *
     *  In the code, the variable names a, b, c, g, h, m, p, q, K, T,
     *  U and X match the names used in "Algorithm HRUA" beginning on
     *  page 82 of Stadlober's 1989 thesis.
     *
     *  It is assumed that when this function is called:
     *    * good, bad and sample are nonnegative;
     *    * the sum good+bad will not result in overflow;
     *    * sample <= good+bad.
     *
     *  References:
     *  -  Ernst Stadlober's thesis "Sampling from Poisson, Binomial and
     *     Hypergeometric Distributions: Ratio of Uniforms as a Simple and
     *     Fast Alternative" (1989)
     *  -  Ernst Stadlober, "The ratio of uniforms approach for generating
     *     discrete random variates", Journal of Computational and Applied
     *     Mathematics, 31, pp. 181-189 (1990).
     */
    public static long hypergeometric_hrua(RandomGenerator bitgen_state,
                                    long good, long bad, long sample) {
        long mingoodbad, maxgoodbad, popsize;
        long computed_sample;
        double p, q;
        double mu, var;
        double a, c, b, h, g;
        long m, K;

        popsize = good + bad;
        computed_sample = Math.min(sample, popsize - sample);
        mingoodbad = Math.min(good, bad);
        maxgoodbad = Math.max(good, bad);

        /*
         *  Variables that do not match Stadlober (1989)
         *    Here               Stadlober
         *    ----------------   ---------
         *    mingoodbad            M
         *    popsize               N
         *    computed_sample       n
         */

        p = ((double) mingoodbad) / popsize;
        q = ((double) maxgoodbad) / popsize;

        // mu is the mean of the distribution.
        mu = computed_sample * p;

        a = mu + 0.5;

        // var is the variance of the distribution.
        var = ((double) (popsize - computed_sample) *
                computed_sample * p * q / (popsize - 1));

        c = Math.sqrt(var + 0.5);

        /*
         *  h is 2*s_hat (See Stadlober's theses (1989), Eq. (5.17); or
         *  Stadlober (1990), Eq. 8).  s_hat is the scale of the "table mountain"
         *  function that dominates the scaled hypergeometric PMF ("scaled" means
         *  normalized to have a maximum value of 1).
         */
        h = D1 * c + D2;

        m = (long) floor((double) (computed_sample + 1) * (mingoodbad + 1) /
                (popsize + 2));

        g = (logfactorial(m) +
                logfactorial(mingoodbad - m) +
                logfactorial(computed_sample - m) +
                logfactorial(maxgoodbad - computed_sample + m));

        /*
         *  b is the upper bound for random samples:
         *  ... min(computed_sample, mingoodbad) + 1 is the length of the support.
         *  ... floor(a + 16*c) is 16 standard deviations beyond the mean.
         *
         *  The idea behind the second upper bound is that values that far out in
         *  the tail have negligible probabilities.
         *
         *  There is a comment in a previous version of this algorithm that says
         *      "16 for 16-decimal-digit precision in D1 and D2",
         *  but there is no documented justification for this value.  A lower value
         *  might work just as well, but I've kept the value 16 here.
         */
        b = Math.min(Math.min(computed_sample, mingoodbad) + 1, floor(a + 16 * c));

        while (true) {
            double U, V, X, T;
            double gp;
            U = bitgen_state.nextDouble();
            V = bitgen_state.nextDouble();  // "U star" in Stadlober (1989)
            X = a + h * (V - 0.5) / U;

            // fast rejection:
            if ((X < 0.0) || (X >= b)) {
                continue;
            }

            K = (long) floor(X);

            gp = (logfactorial(K) +
                    logfactorial(mingoodbad - K) +
                    logfactorial(computed_sample - K) +
                    logfactorial(maxgoodbad - computed_sample + K));

            T = g - gp;

            // fast acceptance:
            if ((U * (4.0 - U) - 3.0) <= T) {
                break;
            }

            // fast rejection:
            if (U * (U - T) >= 1) {
                continue;
            }

            if (2.0 * log(U) <= T) {
                // acceptance
                break;
            }
        }

        if (good > bad) {
            K = computed_sample - K;
        }

        if (computed_sample < sample) {
            K = good - K;
        }

        return K;
    }


    /**
     *  Draw a sample from the hypergeometric distribution.
     *
     *  It is assumed that when this function is called:
     *    * good, bad and sample are nonnegative;
     *    * the sum good+bad will not result in overflow;
     *    * sample <= good+bad.
     */
    public static long random_hypergeometric(RandomGenerator bitgen_state,
                               long good, long bad, long sample) {
        long r;

        if ((sample >= 10) && (sample <= good + bad - 10)) {
            // This will use the ratio-of-uniforms method.
            r = hypergeometric_hrua(bitgen_state, good, bad, sample);
        } else {
            // The simpler implementation is faster for small samples.
            r = hypergeometric_sample(bitgen_state, good, bad, sample);
        }
        return r;
    }
}
