package com.milaboratory.mixcr.tags;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.tags.DropletCloneGraph.CloneTagTupleList;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.Double.isNaN;

/**
 *
 */
public final class CloneTagTupleFilter {
    @JsonProperty("minReadFractionInTag")
    public final double minReadFractionInTag;
    @JsonProperty("maxCloneRankInTag")
    public final int maxCloneRankInTag;
    @JsonProperty("minTagReads")
    public final double minTagReads;
    @JsonProperty("minTagUMIs")
    public final double minTagUMIs;
    @JsonProperty("tagReadsQuantile")
    public final double tagReadsQuantile;
    @JsonProperty("tagReadsQuantileCoefficient")
    public final double tagReadsQuantileCoefficient;
    @JsonProperty("tagUMIsQuantile")
    public final double tagUMIsQuantile;
    @JsonProperty("tagUMIsQuantileCoefficient")
    public final double tagUMIsQuantileCoefficient;

    @JsonCreator
    public CloneTagTupleFilter(@JsonProperty("minReadFractionInTag") double minReadFractionInTag,
                               @JsonProperty("maxCloneRankInTag") int maxCloneRankInTag,
                               @JsonProperty("minTagReads") double minTagReads,
                               @JsonProperty("minTagUMIs") double minTagUMIs,
                               @JsonProperty("tagReadsQuantile") double tagReadsQuantile,
                               @JsonProperty("tagReadsQuantileCoefficient") double tagReadsQuantileCoefficient,
                               @JsonProperty("tagUMIsQuantile") double tagUMIsQuantile,
                               @JsonProperty("tagUMIsQuantileCoefficient") double tagUMIsQuantileCoefficient) {
        if (!isNaN(tagReadsQuantile) && isNaN(tagReadsQuantileCoefficient))
            throw new IllegalArgumentException("Please specify tagReadsQuantileCoefficient.");
        if (!isNaN(tagUMIsQuantile) && isNaN(tagUMIsQuantileCoefficient))
            throw new IllegalArgumentException("Please specify tagUMIsQuantileCoefficient.");
        this.minReadFractionInTag = minReadFractionInTag;
        this.maxCloneRankInTag = maxCloneRankInTag;
        this.minTagReads = minTagReads;
        this.minTagUMIs = minTagUMIs;
        this.tagReadsQuantile = tagReadsQuantile;
        this.tagReadsQuantileCoefficient = tagReadsQuantileCoefficient;
        this.tagUMIsQuantile = tagUMIsQuantile;
        this.tagUMIsQuantileCoefficient = tagUMIsQuantileCoefficient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneTagTupleFilter that = (CloneTagTupleFilter) o;
        return minReadFractionInTag == that.minReadFractionInTag &&
                maxCloneRankInTag == that.maxCloneRankInTag &&
                Double.compare(that.minTagReads, minTagReads) == 0 &&
                Double.compare(that.minTagUMIs, minTagUMIs) == 0 &&
                Double.compare(that.tagReadsQuantile, tagReadsQuantile) == 0 &&
                Double.compare(that.tagUMIsQuantile, tagUMIsQuantile) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minReadFractionInTag, maxCloneRankInTag, minTagReads, minTagUMIs, tagReadsQuantile, tagUMIsQuantile);
    }

    public List<CloneTagTuple> filter(CloneTagTupleList tuples, DropletCloneGraphReport report) {

        double minTagReads = 0.0, minTagUMIs = 0.0;
        if (!isNaN(tagReadsQuantile)) {
            minTagReads = tuples.readsTopQuantile(tagReadsQuantile);
            minTagReads = minTagReads * tagReadsQuantileCoefficient;
        }
        if (!isNaN(tagUMIsQuantile)) {
            minTagUMIs = tuples.umisTopQuantile(tagUMIsQuantile);
            minTagUMIs = minTagUMIs * tagUMIsQuantileCoefficient;
        }

        if (!isNaN(this.minTagReads))
            minTagReads = Math.max(minTagReads, this.minTagReads);
        if (!isNaN(this.minTagUMIs))
            minTagUMIs = Math.max(minTagUMIs, this.minTagUMIs);

        report.effectiveMinReadsInTag = minTagReads;
        report.effectiveMinUMIsInTag = minTagUMIs;

        double minTagReadsFinal = minTagReads;
        double minTagUMIsFinal = minTagUMIs;

        List<CloneTagTuple> result = tuples.tuples.stream().filter(tup -> {

            if (tup.readFractionInTag < this.minReadFractionInTag)
                return false;

            if (tup.rank > this.maxCloneRankInTag)
                return false;

            if (tup.getReadsInTag() < minTagReadsFinal)
                return false;

            if (tup.getUMIsInTag() < minTagUMIsFinal)
                return false;

            return true;
        }).collect(Collectors.toList());


        int tuplesBefore = tuples.tuples.size();
        int tagsBefore = tuples.umisPerDroplet.size();
        long umisBefore = tuples.tuples.stream().mapToLong(s -> s.umiCount).sum();
        double readsBefore = tuples.tuples.stream().mapToDouble(s -> s.readCount).sum();

        int tuplesAfter = result.size();
        int tagsAfter = (int) result.stream().map(s -> s.tag).distinct().count();
        long umisAfter = result.stream().mapToLong(s -> s.umiCount).sum();
        double readsAfter = result.stream().mapToDouble(s -> s.readCount).sum();

        report.tagTuplesTotal.set(tuplesBefore);
        report.tagsTotal.set(tagsBefore);
        report.umisTotal.set(umisBefore);
        report.readsTotal.set(readsBefore);

        report.tagTuplesFiltered.set(tuplesAfter);
        report.tagsFiltered.set(tagsAfter);
        report.umisFiltered.set(umisAfter);
        report.readsFiltered.set(readsAfter);

        return result;
    }
}
