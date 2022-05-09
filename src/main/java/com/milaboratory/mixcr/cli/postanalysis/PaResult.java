package com.milaboratory.mixcr.cli.postanalysis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.util.GlobalObjectMappers;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * PA results (written to disk)
 */
@JsonAutoDetect
public final class PaResult {
    /** Metadata. Null if was not specified */
    @JsonProperty("metadata")
    public final Map<String, List<Object>> metadata;
    /** Metadata categories used to isolate samples into groups */
    @JsonProperty("isolatedGroups")
    public final List<String> isolationGroups;
    /** Results for groups */
    @JsonProperty("results")
    public final List<PaResultByGroup> results;

    @JsonCreator
    public PaResult(@JsonProperty("metadata") Map<String, List<Object>> metadata,
                    @JsonProperty("isolationGroups") List<String> isolationGroups,
                    @JsonProperty("results") List<PaResultByGroup> results) {
        this.metadata = metadata;
        this.isolationGroups = isolationGroups;
        this.results = results;
    }

    public static void writeJson(Path path, PaResult paResult) {
        if (path.getFileName().toString().endsWith(".json")) {
            try {
                GlobalObjectMappers.PRETTY.writeValue(path.toFile(), paResult);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (path.getFileName().toString().endsWith(".json.gz")) {
            try (FileOutputStream fs = new FileOutputStream(path.toFile());
                 GZIPOutputStream zs = new GZIPOutputStream(new BufferedOutputStream(fs))) {
                GlobalObjectMappers.ONE_LINE.writeValue(zs, paResult);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else
            throw new IllegalArgumentException("path should ends with .json.gz or .json but was " + path);
    }

    public static PaResult readJson(Path path) {
        if (path.getFileName().toString().endsWith(".json")) {
            try {
                return GlobalObjectMappers.PRETTY.readValue(path.toFile(), PaResult.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (path.getFileName().toString().endsWith(".json.gz"))
            try (FileInputStream fs = new FileInputStream(path.toFile());
                 GZIPInputStream zs = new GZIPInputStream(new BufferedInputStream(fs))) {
                return GlobalObjectMappers.ONE_LINE.readValue(zs, PaResult.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        else
            throw new IllegalArgumentException("path should ends with .json.gz or .json");
    }
}
