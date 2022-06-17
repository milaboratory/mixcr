/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */

// a will be assigned to alignment
a = null;
V = null;
D = null;
J = null;
C = null;

function length(feature) {
    sq = a.getFeature(feature);
    return sq == null ? 0 : sq.size();
}

function targetMeanQuality(targetIndex) {
    return a.getTarget(targetIndex).getQuality().meanValue();
}

function numberOfTargets() {
    return a.numberOfTargets();
}

function contains(feature) {
    return a.getFeature(feature) != null;
}

function targetAlignedTop(targetIndex, geneType) {
    gts = (typeof(geneType) === 'undefined' ? [V, D, J, C] : [geneType]);
    for (var i = 0; i < gts.length; i++) {
        var gt = gts[i];
        hits = a.getHits(gt);
        if (hits == null)
            continue;
        if (hits.length == 0)
            continue;
        hit = hits[0];
        if (hit.getAlignment(targetIndex) != null)
            return true;
    }
    return false;
}

function targetAlignedAny(targetIndex, geneType) {
    gts = (typeof(geneType) === 'undefined' ? [V, D, J, C] : [geneType]);
    for (var i = 0; i < gts.length; i++) {
        var gt = gts[i];
        hits = a.getHits(gt);
        if (hits == null)
            continue;
        if (hits.length == 0)
            continue;
        for (hit in hits)
            if (hit.getAlignment(targetIndex) != null)
                return true;
    }
    return false;
}

function evaluate_filter() {
    /*CODE*/
    return Boolean(/*FILTER*/);
}
