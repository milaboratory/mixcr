package com.milaboratory.mixcr.basictypes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * to obtain vdjca/clna/clns file: two files with the same pipelines must be identical.
 */
@Serializable(by = PipelineConfiguration.IO.class) // use binary serializer by default (better compression)
@JsonAutoDetect(                                   // enable json format for output purposes
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class PipelineConfiguration {
    /**
     * A sequence of analysis steps performed on the input file
     */
    public final PipelineStep[] pipelineSteps;

    /**
     * @param pipelineSteps pipeline steps
     */
    @JsonCreator
    public PipelineConfiguration(@JsonProperty("pipelineSteps") PipelineStep[] pipelineSteps) {
        this.pipelineSteps = pipelineSteps;
    }

    /** Returns the last written action */
    public ActionConfiguration lastConfiguration() {
        return pipelineSteps[pipelineSteps.length - 1].configuration;
    }

    /** Whether configurations are fully compatible */
    public boolean compatibleWith(PipelineConfiguration oth) {
        if (oth == null)
            return false;
        if (pipelineSteps.length != oth.pipelineSteps.length)
            return false;
        for (int i = 0; i < pipelineSteps.length; ++i)
            if (!pipelineSteps[i].compatibleWith(oth.pipelineSteps[i]))
                return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineConfiguration that = (PipelineConfiguration) o;
        return Arrays.equals(pipelineSteps, that.pipelineSteps);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pipelineSteps);
    }

    /** Appends a new pipeline step */
    public static PipelineConfiguration
    appendStep(PipelineConfiguration history,
               List<String> inputFiles,
               ActionConfiguration configuration) {

        MiXCRVersionInfo versionString = MiXCRVersionInfo.get();

        LightFileDescriptor[] inputDescriptors = inputFiles
                .stream()
                .map(f -> LightFileDescriptor.calculate(Paths.get(f)))
                .toArray(LightFileDescriptor[]::new);

        return new PipelineConfiguration(
                Stream.concat(
                        Stream.of(history.pipelineSteps),
                        Stream.of(new PipelineStep(versionString, inputDescriptors, configuration))
                ).toArray(PipelineStep[]::new));
    }

    /** Creates initial history */
    public static PipelineConfiguration
    mkInitial(List<String> inputFiles,
              ActionConfiguration configuration) {
        MiXCRVersionInfo versionString = MiXCRVersionInfo.get();
        LightFileDescriptor[] inputDescriptors = inputFiles.stream().map(f -> LightFileDescriptor.calculate(Paths.get(f))).toArray(LightFileDescriptor[]::new);
        return new PipelineConfiguration(
                new PipelineStep[]{new PipelineStep(versionString, inputDescriptors, configuration)});
    }

    /** A single step in the analysis pipeline */
    @Serializable(by = PipelineStep.IO.class)
    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class PipelineStep {
        /**
         * Version of MiXCR used
         */
        public final MiXCRVersionInfo versionOfMiXCR;
        /**
         * Hash sum of the input file (not exactly fastq, it may be e.g. vdjca input file for the assemblePartial step)
         */
        public final LightFileDescriptor[] inputFilesDescriptors;
        /**
         * Step configuration
         */
        public final ActionConfiguration configuration;

        @JsonCreator
        public PipelineStep(
                @JsonProperty("versionOfMiXCR") MiXCRVersionInfo versionOfMiXCR,
                @JsonProperty("inputFilesDescriptors") LightFileDescriptor[] inputFilesDescriptors,
                @JsonProperty("configuration") ActionConfiguration configuration) {
            this.versionOfMiXCR = versionOfMiXCR;
            this.inputFilesDescriptors = inputFilesDescriptors;
            this.configuration = configuration;
        }

        /** Compatible with other configuration */
        public boolean compatibleWith(PipelineStep other) {
            return other != null
                    && Arrays.equals(inputFilesDescriptors, other.inputFilesDescriptors)
                    && configuration.getClass().equals(other.configuration.getClass())
                    && configuration.compatibleWith(other.configuration);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PipelineStep that = (PipelineStep) o;
            return Objects.equals(versionOfMiXCR, that.versionOfMiXCR) &&
                    Arrays.equals(inputFilesDescriptors, that.inputFilesDescriptors) &&
                    Objects.equals(configuration, that.configuration);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(versionOfMiXCR, configuration);
            result = 31 * result + Arrays.hashCode(inputFilesDescriptors);
            return result;
        }

        public static final class IO implements Serializer<PipelineStep> {
            @Override
            public void write(PrimitivO output, PipelineStep object) {
                output.writeObject(object.versionOfMiXCR);
                output.writeObject(object.inputFilesDescriptors);
                output.writeObject(object.configuration);
            }

            @Override
            public PipelineStep read(PrimitivI input) {
                MiXCRVersionInfo versionOfMiXCR = input.readObject(MiXCRVersionInfo.class);
                LightFileDescriptor[] inputFilesDescriptors = input.readObject(LightFileDescriptor[].class);
                ActionConfiguration configuration = input.readObject(ActionConfiguration.class);
                return new PipelineStep(versionOfMiXCR, inputFilesDescriptors, configuration);
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

    public static final class IO implements Serializer<PipelineConfiguration> {
        @Override
        public void write(PrimitivO output, PipelineConfiguration object) {
            output.writeObject(object.pipelineSteps);
        }

        @Override
        public PipelineConfiguration read(PrimitivI input) {
            PipelineStep[] pipelineSteps = input.readObject(PipelineStep[].class);
            return new PipelineConfiguration(pipelineSteps);
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
