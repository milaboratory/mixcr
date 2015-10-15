package com.milaboratory.mixcr.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.export.FieldExtractor;
import com.milaboratory.mixcr.export.FieldExtractors;
import com.milaboratory.mixcr.export.OutputMode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
@Parameters(commandDescription = "Export alignments/clones to tab-delimited text file", optionPrefixes = "-")
public class ActionExportParameters extends ActionParametersWithOutput {
    public static final String DEFAULT_PRESET = "full";

    @Parameter(description = "input_file output_file")
    public List<String> files = new ArrayList<>();

    @Parameter(description = "Specify preset of export fields (full, min)",
            names = {"-p", "--preset"})
    public String preset = "full";

    @Parameter(description = "Specify preset file of export fields",
            names = {"-pf", "--preset-file"})
    public String presetFile;

    @Parameter(description = "List available export fields",
            names = {"-l", "--list-fields"})
    public Boolean listFields = false;

    @Parameter(description = "Output short versions of column headers which facilitates analysis with Pandas, R/DataFrames or other data tables processing library.",
            names = {"-s", "--no-spaces"})
    public Boolean noSpaces = false;

    @Parameter(description = "Output only first N records",
            names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
    public long limit = Long.MAX_VALUE;

    public ArrayList<FieldExtractor> exporters;

    @Override
    protected List<String> getOutputFiles() {
        return files.subList(1, 2);
    }

    public String getOutputFile() {
        return files.get(1);
    }

    public boolean printToStdout() {
        return getOutputFile().equals(".");
    }

    public String getInputFile() {
        return files.get(0);
    }

    @Override
    public void validate() {
        if (help || listFields)
            return;
        if (files.size() != 2)
            throw new ParameterException("Input/output file is not specified.");
        super.validate();
    }

    public static void parse(Class clazz, final String[] args, ActionExportParameters parameters) {
        if (args.length < 2)
            throw new RuntimeException();
        parameters.files = new ArrayList<String>() {{
            add(args[args.length - 2]);
            add(args[args.length - 1]);
        }};
        JCommander jc = new JCommander(parameters);
        jc.setAcceptUnknownOptions(true);
        jc.parse(Arrays.copyOf(args, args.length - 2));
        if (!parameters.help && !parameters.listFields) {
            OutputMode outputMode = parameters.noSpaces ? OutputMode.ScriptingFriendly : OutputMode.HumanFriendly;
            parameters.exporters = new ArrayList<>();
            //if preset was explicitly specified
            if (parameters.preset != DEFAULT_PRESET)
                parameters.exporters.addAll(getPresetParameters(outputMode, clazz, parameters.preset));

            if (parameters.presetFile != null)
                parameters.exporters.addAll(parseFile(outputMode, clazz, parameters.presetFile));

            parameters.exporters.addAll(parseFields(outputMode, clazz, jc.getUnknownOptions()));

            if (parameters.exporters.isEmpty())
                parameters.exporters.addAll(getPresetParameters(outputMode, clazz, parameters.preset));
        }
    }

    public static ArrayList<FieldExtractor> parseFields(OutputMode outputMode, Class clazz, List<String> options) {
        ArrayList<FieldExtractor> extractors = new ArrayList<>();
        ArrayList<String> args = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith("-")) {
                if (!args.isEmpty()) {
                    extractors.add(FieldExtractors.parse(outputMode, clazz, args.toArray(new String[args.size()])));
                    args.clear();
                }
            }
            args.add(option.trim());
        }
        if (!args.isEmpty())
            extractors.add(FieldExtractors.parse(outputMode, clazz, args.toArray(new String[args.size()])));
        return extractors;
    }

    public static ArrayList<FieldExtractor> parseFile(OutputMode outputMode, Class clazz, String file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<String> options = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty())
                    options.addAll(Arrays.asList(line.split(" ")));
            }
            return parseFields(outputMode, clazz, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String listOfFields(Class clazz) {
        ArrayList<String>[] description = FieldExtractors.getDescription(clazz);
        return "Available export fields:\n" + Util.printTwoColumns(description[0], description[1], 23, 50, 5, "\n");
    }

    private static final Map<Class, Map<String, String>> presets;

    static {
        presets = new HashMap<>();

        Map<String, String> clones = new HashMap<>();
        clones.put("min", "-count -vHit -dHit -jHit -cHit -nFeature CDR3");
        clones.put("fullNoId", "-count -fraction -sequence -quality " +
                "-vHitsWithScore -dHitsWithScore -jHitsWithScore -cHitsWithScore " +
                "-vAlignments -dAlignments -jAlignments -cAlignments " +
                "-nFeature FR1 -minFeatureQuality FR1 -nFeature CDR1 -minFeatureQuality CDR1 " +
                "-nFeature FR2 -minFeatureQuality FR2 -nFeature CDR2 -minFeatureQuality CDR2 " +
                "-nFeature FR3 -minFeatureQuality FR3 -nFeature CDR3 -minFeatureQuality CDR3 " +
                "-nFeature FR4 -minFeatureQuality FR4 " +
                "-aaFeature FR1 -aaFeature CDR1 -aaFeature FR2 -aaFeature CDR2 " +
                "-aaFeature FR3 -aaFeature CDR3 -aaFeature FR4 -defaultAnchorPoints");
        clones.put("full", "-cloneId -count -fraction -sequence -quality " +
                "-vHitsWithScore -dHitsWithScore -jHitsWithScore -cHitsWithScore " +
                "-vAlignments -dAlignments -jAlignments -cAlignments " +
                "-nFeature FR1 -minFeatureQuality FR1 -nFeature CDR1 -minFeatureQuality CDR1 " +
                "-nFeature FR2 -minFeatureQuality FR2 -nFeature CDR2 -minFeatureQuality CDR2 " +
                "-nFeature FR3 -minFeatureQuality FR3 -nFeature CDR3 -minFeatureQuality CDR3 " +
                "-nFeature FR4 -minFeatureQuality FR4 " +
                "-aaFeature FR1 -aaFeature CDR1 -aaFeature FR2 -aaFeature CDR2 " +
                "-aaFeature FR3 -aaFeature CDR3 -aaFeature FR4 -defaultAnchorPoints");
        presets.put(Clone.class, clones);

        Map<String, String> alignments = new HashMap<>();
        alignments.put("min", "-vHit -dHit -jHit -cHit -nFeature CDR3");
        alignments.put("full", "-sequence -quality " +
                "-vHitsWithScore -dHitsWithScore -jHitsWithScore -cHitsWithScore " +
                "-vAlignments -dAlignments -jAlignments -cAlignments " +
                "-nFeature FR1 -minFeatureQuality FR1 -nFeature CDR1 -minFeatureQuality CDR1 " +
                "-nFeature FR2 -minFeatureQuality FR2 -nFeature CDR2 -minFeatureQuality CDR2 " +
                "-nFeature FR3 -minFeatureQuality FR3 -nFeature CDR3 -minFeatureQuality CDR3 " +
                "-nFeature FR4 -minFeatureQuality FR4 " +
                "-aaFeature FR1 -aaFeature CDR1 -aaFeature FR2 -aaFeature CDR2 " +
                "-aaFeature FR3 -aaFeature CDR3 -aaFeature FR4 -defaultAnchorPoints")
        ;
        presets.put(VDJCAlignments.class, alignments);
    }

    public static ArrayList<FieldExtractor> getPresetParameters(OutputMode mode, Class clazz, String preset) {
        return parseFields(mode, clazz, Arrays.asList(ActionExportParameters.presets.get(clazz).get(preset).split(" ")));
    }
}
