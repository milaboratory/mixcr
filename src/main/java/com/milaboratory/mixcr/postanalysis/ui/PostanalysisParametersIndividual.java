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
package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeCharacteristic;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.*;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class PostanalysisParametersIndividual extends PostanalysisParameters {
    public static final String
            CDR3Metrics = "cdr3metrics",
            Diversity = "diversity",
            VUsage = "vUsage",
            JUsage = "jUsage",
            VJUsage = "vjUsage",
            VFamilyUsage = "vFamilyUsage",
            JFamilyUsage = "jFamilyUsage",
            VJFamilyUsage = "vjFamilyUsage",
            IsotypeUsage = "IsotypeUsage",
            CDR3Spectratype = "CDR3Spectratype",
            VSpectratype = "VSpectratype",
            VSpectratypeMean = "VSpectratypeMean";

    public CDR3MetricsParameters cdr3metrics = new CDR3MetricsParameters();
    public DiversityParameters diversity = new DiversityParameters();
    public MetricParameters vFamilyUsage = new MetricParameters();
    public MetricParameters jFamilyUsage = new MetricParameters();
    public MetricParameters vjFamilyUsage = new MetricParameters();
    public MetricParameters vUsage = new MetricParameters();
    public MetricParameters jUsage = new MetricParameters();
    public MetricParameters vjUsage = new MetricParameters();
    public MetricParameters isotypeUsage = new MetricParameters();
    public MetricParameters cdr3Spectratype = new MetricParameters();
    public MetricParameters vSpectratype = new MetricParameters();
    public MetricParameters vSpectratypeMean = new MetricParameters();

    public List<CharacteristicGroup<?, Clone>> getGroups(Chains chains, TagsInfo tagsInfo) {
        for (WithParentAndTags wpt : Arrays.asList(
                cdr3metrics, diversity,
                vUsage, jUsage, vjUsage,
                vFamilyUsage, jFamilyUsage, vjFamilyUsage,
                isotypeUsage,
                cdr3Spectratype, vSpectratype, vSpectratypeMean
        )) {
            wpt.setParent(this);
            wpt.setTagsInfo(tagsInfo);
        }

        return Arrays.asList(
                cdr3metrics.getGroup(chains),
                diversity.getGroup(chains),
                new CharacteristicGroup<>(VUsage,
                        Arrays.asList(AdditiveCharacteristics.segmentUsage(
                                vUsage.preprocessor(chains),
                                vUsage.weightFunction(),
                                GeneType.Variable)),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(JUsage,
                        Arrays.asList(AdditiveCharacteristics.segmentUsage(
                                jUsage.preprocessor(chains),
                                jUsage.weightFunction(),
                                GeneType.Joining
                        )),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(VJUsage,
                        Arrays.asList(AdditiveCharacteristics.vjSegmentUsage(
                                vjUsage.preprocessor(chains),
                                vjUsage.weightFunction()
                        )),
                        Arrays.asList(new GroupSummary.VJUsage<>())
                ),

                new CharacteristicGroup<>(VFamilyUsage,
                        Arrays.asList(AdditiveCharacteristics.familyUsage(
                                vUsage.preprocessor(chains),
                                vUsage.weightFunction(),
                                GeneType.Variable)),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(JFamilyUsage,
                        Arrays.asList(AdditiveCharacteristics.familyUsage(
                                jUsage.preprocessor(chains),
                                jUsage.weightFunction(),
                                GeneType.Joining
                        )),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(VJFamilyUsage,
                        Arrays.asList(AdditiveCharacteristics.vjFamilyUsage(
                                vjUsage.preprocessor(chains),
                                vjUsage.weightFunction()
                        )),
                        Arrays.asList(new GroupSummary.VJUsage<>())
                ),

                new CharacteristicGroup<>(IsotypeUsage,
                        Arrays.asList(AdditiveCharacteristics.isotypeUsage(
                                isotypeUsage.preprocessor(chains),
                                isotypeUsage.weightFunction()
                        )),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(CDR3Spectratype,
                        Arrays.asList(new SpectratypeCharacteristic("CDR3 spectratype",
                                cdr3Spectratype.preprocessor(chains),
                                cdr3Spectratype.weightFunction(),
                                10,
                                new SpectratypeKeyFunction<>(new KeyFunctions.AAFeature(GeneFeature.CDR3), GeneFeature.CDR3, false))),
                        Collections.singletonList(new GroupSummary.Simple<>())),

                new CharacteristicGroup<>(VSpectratype,
                        Arrays.asList(AdditiveCharacteristics.VSpectratype(
                                vSpectratype.preprocessor(chains),
                                vSpectratype.weightFunction())),
                        Collections.singletonList(new GroupSummary.Simple<>())),

                new CharacteristicGroup<>(VSpectratypeMean,
                        Arrays.asList(AdditiveCharacteristics.VSpectratypeMean(
                                vSpectratypeMean.preprocessor(chains),
                                vSpectratypeMean.weightFunction())),
                        Collections.singletonList(new GroupSummary.Simple<>()))
        );
    }

    public static String[] SUPPORTED_CDR3_METRICS = {
            "cdr3lenAA",
            "cdr3lenNT",
            "ndnLenNT",
            "addedNNT",
            "strength",
            "hydrophobicity",
            "surface",
            "volume",
            "charge"
    };

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class CDR3MetricsParameters implements WithParentAndTags {
        public MetricParameters cdr3lenAA = new MetricParameters();
        public MetricParameters cdr3lenNT = new MetricParameters();
        public MetricParameters ndnLenNT = new MetricParameters();
        public MetricParameters addedNNT = new MetricParameters();
        public MetricParameters strength = new MetricParameters();
        public MetricParameters hydrophobicity = new MetricParameters();
        public MetricParameters surface = new MetricParameters();
        public MetricParameters volume = new MetricParameters();
        public MetricParameters charge = new MetricParameters();

        private List<MetricParameters> list() {
            return Arrays.asList(cdr3lenAA, cdr3lenNT, ndnLenNT, addedNNT, strength, hydrophobicity, surface, volume, charge);
        }

        @Override
        public void setParent(PostanalysisParameters parent) {
            for (MetricParameters m : list()) {
                m.setParent(parent);
            }
        }

        @Override
        public void setTagsInfo(TagsInfo tagsInfo) {
            for (MetricParameters m : list()) {
                m.setTagsInfo(tagsInfo);
            }
        }

        public CharacteristicGroup<?, Clone> getGroup(Chains chains) {
            return new CharacteristicGroup<>(CDR3Metrics,
                    Arrays.asList(
                            lengthOf(cdr3lenNT.preprocessor(chains), cdr3lenNT.weightFunction(), GeneFeature.CDR3, false).setName("CDR3 length, nt"),
                            lengthOf(cdr3lenAA.preprocessor(chains), cdr3lenAA.weightFunction(), GeneFeature.CDR3, true).setName("CDR3 length, aa"),
                            lengthOf(ndnLenNT.preprocessor(chains), ndnLenNT.weightFunction(), GeneFeature.VJJunction, false).setName("NDN length, nt"),
                            addedNucleotides(addedNNT.preprocessor(chains), addedNNT.weightFunction()).setName("Added N, nt"),
                            biophysics(strength.preprocessor(chains), strength.weightFunction(), AAProperties.AAProperty.N2Strength, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Strength"),
                            biophysics(hydrophobicity.preprocessor(chains), hydrophobicity.weightFunction(), AAProperties.AAProperty.N2Hydrophobicity, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Hydrophobicity"),
                            biophysics(surface.preprocessor(chains), surface.weightFunction(), AAProperties.AAProperty.N2Surface, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Surface"),
                            biophysics(volume.preprocessor(chains), volume.weightFunction(), AAProperties.AAProperty.N2Volume, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Volume"),
                            biophysics(charge.preprocessor(chains), charge.weightFunction(), AAProperties.AAProperty.Charge, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Charge")
                    ),
                    Collections.singletonList(new GroupSummary.Simple<>()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CDR3MetricsParameters that = (CDR3MetricsParameters) o;
            return Objects.equals(cdr3lenAA, that.cdr3lenAA) && Objects.equals(cdr3lenNT, that.cdr3lenNT) && Objects.equals(ndnLenNT, that.ndnLenNT) && Objects.equals(addedNNT, that.addedNNT) && Objects.equals(strength, that.strength) && Objects.equals(hydrophobicity, that.hydrophobicity) && Objects.equals(surface, that.surface) && Objects.equals(volume, that.volume) && Objects.equals(charge, that.charge);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cdr3lenAA, cdr3lenNT, ndnLenNT, addedNNT, strength, hydrophobicity, surface, volume, charge);
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class DiversityParameters implements WithParentAndTags {
        public MetricParameters observed = new MetricParameters();
        public MetricParameters shannonWiener = new MetricParameters();
        public MetricParameters chao1 = new MetricParameters();
        public MetricParameters normalizedShannonWienerIndex = new MetricParameters();
        public MetricParameters inverseSimpsonIndex = new MetricParameters();
        public MetricParameters giniIndex = new MetricParameters();
        public MetricParameters d50 = new MetricParameters();
        public MetricParameters efronThisted = new MetricParameters();

        private List<MetricParameters> list() {
            return Arrays.asList(observed, shannonWiener, chao1, normalizedShannonWienerIndex, inverseSimpsonIndex, giniIndex, d50, efronThisted);
        }

        @Override
        public void setParent(PostanalysisParameters parent) {
            for (MetricParameters m : list()) {
                m.setParent(parent);
            }
        }

        @Override
        public void setTagsInfo(TagsInfo tagsInfo) {
            for (MetricParameters m : list()) {
                m.setTagsInfo(tagsInfo);
            }
        }

        public CharacteristicGroup<?, Clone> getGroup(Chains chains) {
            List<Characteristic<?, Clone>> chars = new ArrayList<>(groupBy(
                    new HashMap<DiversityMeasure, PreprocessorAndWeight<Clone>>() {{
                        put(DiversityMeasure.Observed, observed.pwTuple(chains));
                        put(DiversityMeasure.ShannonWiener, shannonWiener.pwTuple(chains));
                        put(DiversityMeasure.Chao1, chao1.pwTuple(chains));
                        put(DiversityMeasure.NormalizedShannonWienerIndex, normalizedShannonWienerIndex.pwTuple(chains));
                        put(DiversityMeasure.InverseSimpsonIndex, inverseSimpsonIndex.pwTuple(chains));
                        put(DiversityMeasure.GiniIndex, giniIndex.pwTuple(chains));
                        put(DiversityMeasure.EfronThisted, efronThisted.pwTuple(chains));
                    }},
                    (p, l) -> Collections.singletonList(new DiversityCharacteristic<>("Diversity "
                            + l.stream().map(m -> m.name).collect(Collectors.joining("/")),
                            p.weight, p.preproc, l.toArray(new DiversityMeasure[0])))));

            chars.add(new DiversityCharacteristic<>("d50", new WeightFunctions.Count(),
                    d50.preprocessor(chains).then(new SelectTop.Factory<>(WeightFunctions.Count, 0.5)),
                    new DiversityMeasure[]{
                            DiversityMeasure.Observed.overrideName("d50")
                    }));

            //noinspection unchecked,rawtypes
            return new CharacteristicGroup(Diversity,
                    chars,
                    Arrays.asList(new GroupSummary.Simple<>())
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DiversityParameters that = (DiversityParameters) o;
            return Objects.equals(observed, that.observed) && Objects.equals(shannonWiener, that.shannonWiener) && Objects.equals(chao1, that.chao1) && Objects.equals(normalizedShannonWienerIndex, that.normalizedShannonWienerIndex) && Objects.equals(inverseSimpsonIndex, that.inverseSimpsonIndex) && Objects.equals(giniIndex, that.giniIndex) && Objects.equals(d50, that.d50);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observed, shannonWiener, chao1, normalizedShannonWienerIndex, inverseSimpsonIndex, giniIndex, d50);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostanalysisParametersIndividual that = (PostanalysisParametersIndividual) o;
        return Objects.equals(cdr3metrics, that.cdr3metrics) && Objects.equals(diversity, that.diversity) && Objects.equals(vFamilyUsage, that.vFamilyUsage) && Objects.equals(jFamilyUsage, that.jFamilyUsage) && Objects.equals(vjFamilyUsage, that.vjFamilyUsage) && Objects.equals(vUsage, that.vUsage) && Objects.equals(jUsage, that.jUsage) && Objects.equals(vjUsage, that.vjUsage) && Objects.equals(isotypeUsage, that.isotypeUsage) && Objects.equals(cdr3Spectratype, that.cdr3Spectratype) && Objects.equals(vSpectratype, that.vSpectratype) && Objects.equals(vSpectratypeMean, that.vSpectratypeMean);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cdr3metrics, diversity, vFamilyUsage, jFamilyUsage, vjFamilyUsage, vUsage, jUsage, vjUsage, isotypeUsage, cdr3Spectratype, vSpectratype, vSpectratypeMean);
    }
}
