package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeCharacteristic;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.*;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class PostanalysisParametersIndividual extends PostanalysisParameters {
    public static final String
            Biophysics = "biophysics",
            Diversity = "diversity",
            VUsage = "vUsage",
            JUsage = "JUsage",
            VJUsage = "VJUsage",
            IsotypeUsage = "IsotypeUsage",
            CDR3Spectratype = "CDR3Spectratype",
            VSpectratype = "VSpectratype",
            VSpectratypeMean = "VSpectratypeMean";

    public BiophysicsParameters biophysics = new BiophysicsParameters();
    public DiversityParameters diversity = new DiversityParameters();
    public PreprocessorParameters vUsage = new PreprocessorParameters();
    public PreprocessorParameters jUsage = new PreprocessorParameters();
    public PreprocessorParameters vjUsage = new PreprocessorParameters();
    public PreprocessorParameters isotypeUsage = new PreprocessorParameters();
    public PreprocessorParameters cdr3Spectratype = new PreprocessorParameters();
    public PreprocessorParameters vSpectratype = new PreprocessorParameters();
    public PreprocessorParameters vSpectratypeMean = new PreprocessorParameters();

    public List<CharacteristicGroup<?, Clone>> getGroups() {
        return Arrays.asList(
                biophysics.getGroup(this),
                diversity.getGroup(this),
                new CharacteristicGroup<>(VUsage,
                        Arrays.asList(AdditiveCharacteristics.segmentUsage(vUsage.preproc(this), GeneType.Variable)),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(JUsage,
                        Arrays.asList(AdditiveCharacteristics.segmentUsage(jUsage.preproc(this), GeneType.Joining)),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(VJUsage,
                        Arrays.asList(AdditiveCharacteristics.vjSegmentUsage(vjUsage.preproc(this))),
                        Arrays.asList(new GroupSummary.VJUsage<>())
                ),

                new CharacteristicGroup<>(IsotypeUsage,
                        Arrays.asList(AdditiveCharacteristics.isotypeUsage(isotypeUsage.preproc(this))),
                        Arrays.asList(new GroupSummary.Simple<>())
                ),

                new CharacteristicGroup<>(CDR3Spectratype,
                        Arrays.asList(new SpectratypeCharacteristic("CDR3 spectratype",
                                cdr3Spectratype.preproc(this), 10,
                                new SpectratypeKeyFunction<>(new KeyFunctions.AAFeature(GeneFeature.CDR3), GeneFeature.CDR3, false))),
                        Collections.singletonList(new GroupSummary.Simple<>())),

                new CharacteristicGroup<>(VSpectratype,
                        Arrays.asList(AdditiveCharacteristics.VSpectratype(vSpectratype.preproc(this))),
                        Collections.singletonList(new GroupSummary.Simple<>())),

                new CharacteristicGroup<>(VSpectratypeMean,
                        Arrays.asList(AdditiveCharacteristics.VSpectratypeMean(vSpectratypeMean.preproc(this))),
                        Collections.singletonList(new GroupSummary.Simple<>()))
        );
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class BiophysicsParameters {
        public PreprocessorParameters cdr3lenAA = new PreprocessorParameters();
        public PreprocessorParameters cdr3lenNT = new PreprocessorParameters();
        public PreprocessorParameters ndnLenNT = new PreprocessorParameters();
        public PreprocessorParameters addedNNT = new PreprocessorParameters();
        public PreprocessorParameters Strength = new PreprocessorParameters();
        public PreprocessorParameters Hydrophobicity = new PreprocessorParameters();
        public PreprocessorParameters Surface = new PreprocessorParameters();
        public PreprocessorParameters Volume = new PreprocessorParameters();
        public PreprocessorParameters Charge = new PreprocessorParameters();

        public CharacteristicGroup<?, Clone> getGroup(PostanalysisParameters base) {
            return new CharacteristicGroup<>(Biophysics,
                    Arrays.asList(
                            weightedLengthOf(cdr3lenNT.preproc(base), GeneFeature.CDR3, false).setName("CDR3 length, nt"),
                            weightedLengthOf(cdr3lenAA.preproc(base), GeneFeature.CDR3, true).setName("CDR3 length, aa"),
                            weightedLengthOf(ndnLenNT.preproc(base), GeneFeature.VJJunction, false).setName("NDN length, nt"),
                            weightedAddedNucleotides(addedNNT.preproc(base)).setName("Added N, nt"),
                            weightedBiophysics(Strength.preproc(base), AAProperties.AAProperty.N2Strength, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Strength"),
                            weightedBiophysics(Hydrophobicity.preproc(base), AAProperties.AAProperty.N2Hydrophobicity, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Hydrophobicity"),
                            weightedBiophysics(Surface.preproc(base), AAProperties.AAProperty.N2Surface, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Surface"),
                            weightedBiophysics(Volume.preproc(base), AAProperties.AAProperty.N2Volume, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Volume"),
                            weightedBiophysics(Charge.preproc(base), AAProperties.AAProperty.Charge, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Charge")
                    ),
                    Collections.singletonList(new GroupSummary.Simple<>()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BiophysicsParameters that = (BiophysicsParameters) o;
            return Objects.equals(cdr3lenAA, that.cdr3lenAA) && Objects.equals(cdr3lenNT, that.cdr3lenNT) && Objects.equals(ndnLenNT, that.ndnLenNT) && Objects.equals(addedNNT, that.addedNNT) && Objects.equals(Strength, that.Strength) && Objects.equals(Hydrophobicity, that.Hydrophobicity) && Objects.equals(Surface, that.Surface) && Objects.equals(Volume, that.Volume) && Objects.equals(Charge, that.Charge);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cdr3lenAA, cdr3lenNT, ndnLenNT, addedNNT, Strength, Hydrophobicity, Surface, Volume, Charge);
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class DiversityParameters {
        public PreprocessorParameters observed = new PreprocessorParameters();
        public PreprocessorParameters shannonWiener = new PreprocessorParameters();
        public PreprocessorParameters chao1 = new PreprocessorParameters();
        public PreprocessorParameters clonality = new PreprocessorParameters();
        public PreprocessorParameters inverseSimpson = new PreprocessorParameters();
        public PreprocessorParameters gini = new PreprocessorParameters();
        public PreprocessorParameters d50 = new PreprocessorParameters();

        public CharacteristicGroup<?, Clone> getGroup(PostanalysisParameters base) {
            List<Characteristic<?, Clone>> chars = new ArrayList<>(groupByPreproc(
                    new HashMap<DiversityMeasure, SetPreprocessorFactory<Clone>>() {{
                        put(DiversityMeasure.Observed, observed.preproc(base));
                        put(DiversityMeasure.ShannonWiener, shannonWiener.preproc(base));
                        put(DiversityMeasure.Chao1, chao1.preproc(base));
                        put(DiversityMeasure.NormalizedShannonWeinerIndex, clonality.preproc(base));
                        put(DiversityMeasure.InverseSimpson, inverseSimpson.preproc(base));
                        put(DiversityMeasure.GiniIndex, gini.preproc(base));
                    }},
                    (p, l) -> Collections.singletonList(new DiversityCharacteristic<>("Diversity "
                            + l.stream().map(m -> m.name).collect(Collectors.joining("/")),
                            new WeightFunctions.Count(), p, l.toArray(new DiversityMeasure[0])))));

            chars.add(new DiversityCharacteristic<>("d50", new WeightFunctions.Count(),
                    d50.preproc(base).then(new SelectTop.Factory<>(WeightFunctions.Count, 0.5)),
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
            return Objects.equals(observed, that.observed) && Objects.equals(shannonWiener, that.shannonWiener) && Objects.equals(chao1, that.chao1) && Objects.equals(clonality, that.clonality) && Objects.equals(inverseSimpson, that.inverseSimpson) && Objects.equals(gini, that.gini) && Objects.equals(d50, that.d50);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observed, shannonWiener, chao1, clonality, inverseSimpson, gini, d50);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostanalysisParametersIndividual that = (PostanalysisParametersIndividual) o;
        return Objects.equals(biophysics, that.biophysics) && Objects.equals(diversity, that.diversity) && Objects.equals(vUsage, that.vUsage) && Objects.equals(jUsage, that.jUsage) && Objects.equals(vjUsage, that.vjUsage) && Objects.equals(isotypeUsage, that.isotypeUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(biophysics, diversity, vUsage, jUsage, vjUsage, isotypeUsage);
    }
}
