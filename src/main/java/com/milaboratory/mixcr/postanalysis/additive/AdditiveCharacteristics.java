package com.milaboratory.mixcr.postanalysis.additive;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions.VJGenes;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

/**
 *
 */
public final class AdditiveCharacteristics {
    private AdditiveCharacteristics() {}

    public static final String readCountKey = "readCount";

    public static AdditiveCharacteristic<String, Clone> readCount(SetPreprocessor<Clone> preproc) {
        return new AdditiveCharacteristic<>(readCountKey, preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.Named<>(readCountKey),
                new AdditiveMetrics.Constant(),
                AggregationType.Sum,
                false);
    }

    public static final String cloneCountKey = "cloneCount";

    public static AdditiveCharacteristic<String, Clone> clonotypeCount(SetPreprocessor<Clone> preproc) {
        return new AdditiveCharacteristic<>(cloneCountKey, preproc,
                new WeightFunctions.NoWeight<>(),
                new KeyFunctions.Named<>(cloneCountKey),
                new AdditiveMetrics.Constant(),
                AggregationType.Sum,
                false);
    }

    public static AdditiveCharacteristic<String, Clone> weightedLengthOf(GeneFeature gf, boolean aa) {
        return weightedLengthOf(new NoPreprocessing<>(), gf, aa);
    }

    public static AdditiveCharacteristic<String, Clone> weightedLengthOf(SetPreprocessor<Clone> preproc, GeneFeature gf, boolean aa) {
        String name = (aa ? "aa" : "nt") + "LengthOf" + GeneFeature.encode(gf);
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.GeneFeatureLength<>(gf, aa),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> weightedBbiophysicsNormalized(AAProperties.AAProperty property, GeneFeature gf) {
        return weightedBbiophysicsNormalized(new NoPreprocessing<>(), property, gf);
    }

    public static AdditiveCharacteristic<String, Clone> weightedBbiophysicsNormalized(SetPreprocessor<Clone> preproc, AAProperties.AAProperty property, GeneFeature gf) {
        String name = property.name() + "of" + GeneFeature.encode(gf) + "Normalized";
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.AAPropertyNormalized(property, gf),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> weightedBiophysics(AAProperties.AAProperty property, GeneFeature gf, AAProperties.Adjustment adjustment, int nLetters) {
        return weightedBiophysics(new NoPreprocessing<>(), property, gf, adjustment, nLetters);
    }

    public static AdditiveCharacteristic<String, Clone> weightedBiophysics(SetPreprocessor<Clone> preproc, AAProperties.AAProperty property, GeneFeature gf, AAProperties.Adjustment adjustment, int nLetters) {
        String name = property.name() + "of" + GeneFeature.encode(gf);
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.AAPropertySum(property, gf, adjustment, nLetters),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(GeneType geneType, KeyFunction<String, Clone> keyFunction) {
        return segmentUsage(new NoPreprocessing<>(), geneType, keyFunction);
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(SetPreprocessor<Clone> preproc, GeneType geneType, KeyFunction<String, Clone> keyFunction) {
        String name = geneType.getLetter() + "Usage";
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                new WeightFunctions.Count(),
                keyFunction,
                new AdditiveMetrics.Constant(1),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(GeneType geneType) {
        return segmentUsage(new NoPreprocessing<>(), geneType);
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(SetPreprocessor<Clone> preproc, GeneType geneType) {
        return segmentUsage(preproc, geneType, new KeyFunctions.SegmentUsage<>(geneType))
                .setName(geneType.getLetter() + "SegmentUsage");
    }

    public static AdditiveCharacteristic<String, Clone> geneUsage(GeneType geneType) {
        return geneUsage(new NoPreprocessing<>(), geneType);
    }

    public static AdditiveCharacteristic<String, Clone> geneUsage(SetPreprocessor<Clone> preproc, GeneType geneType) {
        return segmentUsage(preproc, geneType, new KeyFunctions.GeneUsage<>(geneType))
                .setName(geneType.getLetter() + "GeneUsage");
    }

    public static AdditiveCharacteristic<String, Clone> familyUsage(GeneType geneType) {
        return familyUsage(new NoPreprocessing<>(), geneType);
    }

    public static AdditiveCharacteristic<String, Clone> familyUsage(SetPreprocessor<Clone> preproc, GeneType geneType) {
        return segmentUsage(preproc, geneType, new KeyFunctions.FamiltyUsage<>(geneType))
                .setName(geneType.getLetter() + "FamilyUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjUsage(KeyFunction<VJGenes<String>, Clone> keyFunction) {
        return vjUsage(new NoPreprocessing<>(), keyFunction);
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjUsage(SetPreprocessor<Clone> preproc, KeyFunction<VJGenes<String>, Clone> keyFunction) {
        return new AdditiveCharacteristic<>(
                "VJUsage",
                preproc,
                new WeightFunctions.Count(),
                keyFunction,
                new AdditiveMetrics.Constant(1),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjSegmentUsage() {
        return vjSegmentUsage(new NoPreprocessing<>());
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjSegmentUsage(SetPreprocessor<Clone> preproc) {
        return vjUsage(preproc, new KeyFunctions.VJSegmentUsage<>()).setName("VJSegmentUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjGeneUsage() {
        return vjGeneUsage(new NoPreprocessing<>());
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjGeneUsage(SetPreprocessor<Clone> preproc) {
        return vjUsage(preproc, new KeyFunctions.VJGeneUsage<>()).setName("VJGeneUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjFamilysage() {
        return vjFamilysage(new NoPreprocessing<>());
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjFamilysage(SetPreprocessor<Clone> preproc) {
        return vjUsage(preproc, new KeyFunctions.VJFamilyUsage<>()).setName("VJFamilyUsage");
    }

    public static AdditiveCharacteristic<KeyFunctions.Isotype, Clone> isotypeUsage() {
        return isotypeUsage(new NoPreprocessing<>());
    }

    public static AdditiveCharacteristic<KeyFunctions.Isotype, Clone> isotypeUsage(SetPreprocessor<Clone> preproc) {
        return new AdditiveCharacteristic<>(
                "IsotypeUsage",
                preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.IsotypeUsage<>(),
                new AdditiveMetrics.Constant(1),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<SpectratypeKey<String>, Clone> VSpectratype() {
        return VSpectratype(new NoPreprocessing<>());
    }

    public static AdditiveCharacteristic<SpectratypeKey<String>, Clone> VSpectratype(SetPreprocessor<Clone> preproc) {
        return new AdditiveCharacteristic<>("VSpectratype",
                preproc,
                new WeightFunctions.Count(),
                new SpectratypeKeyFunction<>(new KeyFunctions.SegmentUsage<>(GeneType.Variable), GeneFeature.CDR3, false),
                new AdditiveMetrics.Constant(), AggregationType.Sum, false);
    }

    public static AdditiveCharacteristic<String, Clone> VSpectratypeMean(SetPreprocessor<Clone> preproc) {
        return new AdditiveCharacteristic<>(
                "VSpectratypeMean",
                preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.SegmentUsage<>(GeneType.Variable),
                new AdditiveMetrics.GeneFeatureLength<>(GeneFeature.CDR3, false),
                AggregationType.Mean,
                true
        );
    }
}
