package com.milaboratory.mixcr.cli;

import cc.redberry.primitives.Filter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.export.FieldExtractor;
import com.milaboratory.mixcr.export.FieldExtractors;
import com.milaboratory.mixcr.export.OutputMode;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
@Parameters(commandDescription = "Export alignments/clones to tab-delimited text file")
public class ActionExportParameters<T extends VDJCObject> extends ActionParametersWithOutput {
    public static final String DEFAULT_PRESET = "full";

    @Parameter(description = "input_file output_file")
    public List<String> files = new ArrayList<>();

    @Parameter(description = "Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated)",
            names = {"-c", "--chains"})
    public String chains = "ALL";

    @Deprecated
    @Parameter(description = "Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated)",
            names = {"-l", "--loci"}, hidden = true)
    public String chains_legacy = null;

    @Parameter(description = "Specify preset of export fields (possible values: 'full', 'min'; 'full' by default)",
            names = {"-p", "--preset"})
    public String preset;

    @Parameter(description = "Specify preset file of export fields",
            names = {"-pf", "--preset-file"})
    public String presetFile;

    @Parameter(description = "List available export fields",
            names = {"-lf", "--list-fields"})
    public Boolean listFields = false;

    @Deprecated
    @Parameter(description = "Output short versions of column headers which facilitates analysis with Pandas, R/DataFrames or other data tables processing library.",
            names = {"-s", "--no-spaces"})
    public Boolean noSpaces;

    @Parameter(description = "Output column headers with spaces.",
            names = {"-v", "--with-spaces"})
    public Boolean humanReadable;

    @Parameter(description = "Output only first N records",
            names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
    private Long limit = null;

    public ArrayList<FieldExtractor> exporters;

    public long getLimit() {
        return limit == null ? Long.MAX_VALUE : limit;
    }

    @Override
    protected List<String> getOutputFiles() {
        return files.subList(1, 2);
    }

    public boolean isHumanReadableParameters() {
        return humanReadable != null && humanReadable;
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

    public Chains getChains() {
        if (chains_legacy != null) {
            if (!chains.equals("ALL"))
                throw new ParameterException("Use -c without -l parameter.");
            System.out.println("WARNING: using of -l (--loci) option is deprecated; use -c (--chains) instead.");
            return Chains.parse(chains_legacy);
        }
        return Chains.parse(chains);
    }

    @SuppressWarnings("unchecked")
    public Filter<T> getFilter() {
        List<Filter<T>> filters = new ArrayList<>();

        final Chains chains = getChains();
        filters.add(new Filter<T>() {
            @Override
            public boolean accept(T object) {
                for (GeneType gt : GeneType.VJC_REFERENCE) {
                    VDJCHit bestHit = object.getBestHit(gt);
                    if (bestHit != null && chains.intersects(bestHit.getGene().getChains()))
                        return true;
                }
                return false;
            }
        });

        if (filters.isEmpty())
            return ACCEPT_ALL;

        if (filters.size() == 1)
            return filters.get(0);

        return and(filters.toArray(new Filter[filters.size()]));
    }

    @Override
    public void validate() {
        if (help || listFields)
            return;
        if (files.size() != 2)
            throw new ParameterException("Output file is not specified.");
        super.validate();
    }

    public static void parse(Class clazz, final String[] args, ActionExportParameters parameters) {
        if (parameters.noSpaces != null)
            System.out.println("\"-s\" / \"--no-spaces\" option is deprecated.\nScripting friendly output format now used " +
                    "by default.\nUse \"-v\" / \"--with-spaces\" to switch back to human readable format.");

        JCommander jc = new JCommander(parameters);
        jc.setAcceptUnknownOptions(true);
        jc.parse(cutArgs(args));

        if (!parameters.help && !parameters.listFields) {
            if (args.length < 2)
                throw new ParameterException("Output file is not specified.");
            parameters.files = new ArrayList<String>() {{
                add(args[args.length - 2]);
                add(args[args.length - 1]);
            }};
            OutputMode outputMode = parameters.isHumanReadableParameters() ? OutputMode.HumanFriendly : OutputMode.ScriptingFriendly;
            parameters.exporters = new ArrayList<>();

            //if preset was explicitly specified
            if (parameters.preset != null)
                parameters.exporters.addAll(getPresetParameters(outputMode, clazz, parameters.preset));

            if (parameters.presetFile != null)
                parameters.exporters.addAll(parseFile(outputMode, clazz, parameters.presetFile));

            parameters.exporters.addAll(parseFields(outputMode, clazz, jc.getUnknownOptions()));

            if (parameters.exporters.isEmpty())
                parameters.exporters.addAll(getPresetParameters(outputMode, clazz, DEFAULT_PRESET));

            parameters.validate();
        }
    }

    private static String[] cutArgs(String[] args) {
        int i = 0;
        if (args.length > 0 && !args[args.length - 1].startsWith("-"))
            ++i;
        if (args.length > 1 && !args[args.length - 2].startsWith("-"))
            ++i;
        return Arrays.copyOf(args, args.length - i);
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
                line = line.replace("\"", "");
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
        return "Available export fields:\n\n" + Util.printTwoColumns(description[0], description[1], 45, 70, 5, "\n");
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
                "-aaFeature FR3 -aaFeature CDR3 -defaultAnchorPoints");
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
