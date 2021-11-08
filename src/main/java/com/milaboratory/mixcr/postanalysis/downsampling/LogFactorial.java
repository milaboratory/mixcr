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

/**
 * Adapted from nymphy: https://github.com/numpy/numpy/tree/master/numpy/random/src/distributions
 */
public class LogFactorial {

    /**
     *  logfact[k] holds log(k!) for k = 0, 1, 2, ..., 125.
     */
    private static final double[] logfact = {
            0,
            0,
            0.69314718055994529,
            1.791759469228055,
            3.1780538303479458,
            4.7874917427820458,
            6.5792512120101012,
            8.5251613610654147,
            10.604602902745251,
            12.801827480081469,
            15.104412573075516,
            17.502307845873887,
            19.987214495661885,
            22.552163853123425,
            25.19122118273868,
            27.89927138384089,
            30.671860106080672,
            33.505073450136891,
            36.395445208033053,
            39.339884187199495,
            42.335616460753485,
            45.380138898476908,
            48.471181351835227,
            51.606675567764377,
            54.784729398112319,
            58.003605222980518,
            61.261701761002001,
            64.557538627006338,
            67.88974313718154,
            71.257038967168015,
            74.658236348830158,
            78.092223553315307,
            81.557959456115043,
            85.054467017581516,
            88.580827542197682,
            92.136175603687093,
            95.719694542143202,
            99.330612454787428,
            102.96819861451381,
            106.63176026064346,
            110.32063971475739,
            114.03421178146171,
            117.77188139974507,
            121.53308151543864,
            125.3172711493569,
            129.12393363912722,
            132.95257503561632,
            136.80272263732635,
            140.67392364823425,
            144.5657439463449,
            148.47776695177302,
            152.40959258449735,
            156.3608363030788,
            160.3311282166309,
            164.32011226319517,
            168.32744544842765,
            172.35279713916279,
            176.39584840699735,
            180.45629141754378,
            184.53382886144948,
            188.6281734236716,
            192.7390472878449,
            196.86618167289001,
            201.00931639928152,
            205.1681994826412,
            209.34258675253685,
            213.53224149456327,
            217.73693411395422,
            221.95644181913033,
            226.1905483237276,
            230.43904356577696,
            234.70172344281826,
            238.97838956183432,
            243.26884900298271,
            247.57291409618688,
            251.89040220972319,
            256.22113555000954,
            260.56494097186322,
            264.92164979855278,
            269.29109765101981,
            273.67312428569369,
            278.06757344036612,
            282.4742926876304,
            286.89313329542699,
            291.32395009427029,
            295.76660135076065,
            300.22094864701415,
            304.68685676566872,
            309.1641935801469,
            313.65282994987905,
            318.1526396202093,
            322.66349912672615,
            327.1852877037752,
            331.71788719692847,
            336.26118197919845,
            340.81505887079902,
            345.37940706226686,
            349.95411804077025,
            354.53908551944079,
            359.1342053695754,
            363.73937555556347,
            368.35449607240474,
            372.97946888568902,
            377.61419787391867,
            382.25858877306001,
            386.91254912321756,
            391.57598821732961,
            396.24881705179155,
            400.93094827891576,
            405.6222961611449,
            410.32277652693733,
            415.03230672824964,
            419.75080559954472,
            424.47819341825709,
            429.21439186665157,
            433.95932399501481,
            438.71291418612117,
            443.47508812091894,
            448.24577274538461,
            453.02489623849613,
            457.81238798127816,
            462.60817852687489,
            467.4121995716082,
            472.22438392698058,
            477.04466549258564,
            481.87297922988796
    };

    private static final double halfln2pi = 0.9189385332046728;

    /**
     *  Compute log(k!)
     */
    public static double logfactorial(long k)
    {

        if (k < logfact.length) {
            /* Use the lookup table. */
            return logfact[(int)k];
        }

        /*
         *  Use the Stirling series, truncated at the 1/k**3 term.
         *  (In a Python implementation of this approximation, the result
         *  was within 2 ULP of the best 64 bit floating point value for
         *  k up to 10000000.)
         */
        return (k + 0.5)*Math.log(k) - k + (halfln2pi + (1.0/k)*(1/12.0 - 1/(360.0*k*k)));
    }
}
