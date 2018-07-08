package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.LightFileDescriptor;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This class stores the sequence of analysis steps (that is their unique configurations) applied to the raw fastq file
 * to obtain vdjca/clna/clns file: two files with the same history must be identical.
 */
@Serializable(by = AnalysisHistory.IO.class)
public final class AnalysisHistory {
    /**
     * Version of MiXCR used
     */
    public final String versionOfMiXCR;
    /**
     * Hashsum of the very initial (fastq/fasta) files
     */
    public final LightFileDescriptor[] initialFileDescriptors;
    /**
     * A sequence of analysis steps performed on the input file
     */
    public final AnalysisStep[] analysisSteps;

    public AnalysisHistory(String versionOfMiXCR, LightFileDescriptor[] initialFileDescriptors, AnalysisStep[] analysisSteps) {
        this.versionOfMiXCR = versionOfMiXCR;
        this.initialFileDescriptors = initialFileDescriptors;
        this.analysisSteps = analysisSteps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalysisHistory that = (AnalysisHistory) o;
        return Objects.equals(versionOfMiXCR, that.versionOfMiXCR) &&
                Arrays.equals(initialFileDescriptors, that.initialFileDescriptors) &&
                Arrays.equals(analysisSteps, that.analysisSteps);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(versionOfMiXCR);
        result = 31 * result + Arrays.hashCode(initialFileDescriptors);
        result = 31 * result + Arrays.hashCode(analysisSteps);
        return result;
    }

    /**
     * Appends a new analysis step to the history object
     */
    public static AnalysisHistory appendStep(AnalysisHistory history,
                                             List<String> inputFiles,
                                             ActionConfiguration configuration) {
        LightFileDescriptor[] inputDescriptors = inputFiles.stream().map(f -> LightFileDescriptor.calculate(Paths.get(f))).toArray(LightFileDescriptor[]::new);
        return new AnalysisHistory(history.versionOfMiXCR,
                history.initialFileDescriptors,
                Stream.concat(
                        Stream.of(history.analysisSteps),
                        Stream.of(new AnalysisStep(inputDescriptors, configuration))
                ).toArray(AnalysisStep[]::new));
    }

    /**
     * Creates initial history
     */
    public static AnalysisHistory mkInitial(List<String> inputFiles,
                                            ActionConfiguration configuration) {
        String versionString = MiXCRVersionInfo.get().getVersionString(MiXCRVersionInfo.OutputType.ToFile);
        LightFileDescriptor[] inputDescriptors = inputFiles.stream().map(f -> LightFileDescriptor.calculate(Paths.get(f))).toArray(LightFileDescriptor[]::new);
        return new AnalysisHistory(
                versionString,
                inputDescriptors,
                new AnalysisStep[]{new AnalysisStep(inputDescriptors, configuration)});
    }

    @Serializable(by = AnalysisStep.IO.class)
    public static final class AnalysisStep {
        /**
         * MD5 hash of the input file (not exactly fastq, it may be e.g. vdjca input file for the assemblePartial step)
         */
        public final LightFileDescriptor[] inputFilesDescriptors;
        /**
         * Step configuration
         */
        public final ActionConfiguration configuration;

        public AnalysisStep(
                LightFileDescriptor[] inputFilesDescriptors,
                ActionConfiguration configuration) {
            this.inputFilesDescriptors = inputFilesDescriptors;
            this.configuration = configuration;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AnalysisStep that = (AnalysisStep) o;
            return Arrays.equals(inputFilesDescriptors, that.inputFilesDescriptors) &&
                    Objects.equals(configuration, that.configuration);
        }

        @Override
        public int hashCode() {

            int result = Objects.hash(configuration);
            result = 31 * result + Arrays.hashCode(inputFilesDescriptors);
            return result;
        }

        public static final class IO implements Serializer<AnalysisStep> {
            @Override
            public void write(PrimitivO output, AnalysisStep object) {
                output.writeObject(object.inputFilesDescriptors);
                output.writeObject(object.configuration);
            }

            @Override
            public AnalysisStep read(PrimitivI input) {
                LightFileDescriptor[] inputFilesDescriptors = input.readObject(LightFileDescriptor[].class);
                ActionConfiguration configuration = input.readObject(ActionConfiguration.class);
                return new AnalysisStep(inputFilesDescriptors, configuration);
            }

            @Override
            public boolean isReference() {
                return true;
            }

            @Override
            public boolean handlesReference() {
                return false;
            }
        }
    }

    public static final class IO implements Serializer<AnalysisHistory> {
        @Override
        public void write(PrimitivO output, AnalysisHistory object) {
            output.writeUTF(object.versionOfMiXCR);
            output.writeObject(object.initialFileDescriptors);
            output.writeObject(object.analysisSteps);
        }

        @Override
        public AnalysisHistory read(PrimitivI input) {
            String versionOfMiXCR = input.readUTF();
            LightFileDescriptor[] initialFileDescriptors = input.readObject(LightFileDescriptor[].class);
            AnalysisStep[] analysisSteps = input.readObject(AnalysisStep[].class);
            return new AnalysisHistory(versionOfMiXCR, initialFileDescriptors, analysisSteps);
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }
}
