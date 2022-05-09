package com.milaboratory.mixcr.postanalysis.downsampling;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.LongStream;

/**
 *
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DownsampleValueChooser.Fixed.class, name = "fixed"),
        @JsonSubTypes.Type(value = DownsampleValueChooser.Minimal.class, name = "min"),
        @JsonSubTypes.Type(value = DownsampleValueChooser.Auto.class, name = "auto")
})
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public interface DownsampleValueChooser {
    long compute(long[] totalCounts);

    /** Used for {@link SetPreprocessorFactory#id()} */
    default String id() {return "";}

    class Fixed implements DownsampleValueChooser {
        public long value;

        public Fixed() {}

        public Fixed(long value) {
            this.value = value;
        }

        @Override
        public String id() {
            return "to count " + value;
        }

        @Override
        public long compute(long[] totalCounts) {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Fixed fixed = (Fixed) o;
            return value == fixed.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    class Minimal implements DownsampleValueChooser {
        @Override
        public String id() {
            return "to minimal count";
        }

        @Override
        public long compute(long[] totalCounts) {
            return LongStream.of(totalCounts).min().orElse(0);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }

    class Auto implements DownsampleValueChooser {
        public double quantile = 20.;
        public double scale = 0.5;
        public long threshold = 500;

        public Auto() {}

        public Auto(double quantile, double scale, long threshold) {
            this.quantile = quantile;
            this.scale = scale;
            this.threshold = threshold;
        }

        @Override
        public String id() {
//            return String.format("min(%s*percentile(%s),%s))", scale, quantile, threshold);
            return "automatic";
        }

        @Override
        public long compute(long... totalCounts) {
            long q = (long) (new Percentile(quantile).evaluate(Arrays.stream(totalCounts).mapToDouble(l -> l).toArray()) * scale);
            long min = Arrays.stream(totalCounts).min().orElse(0);
            long d = Math.max(q, min);
            return Math.max(d, threshold);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Auto auto = (Auto) o;
            return Double.compare(auto.quantile, quantile) == 0 &&
                    Double.compare(auto.scale, scale) == 0 &&
                    threshold == auto.threshold;
        }

        @Override
        public int hashCode() {
            return Objects.hash(quantile, scale, threshold);
        }
    }
}
