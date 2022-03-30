package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.ACommandMiXCR;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.util.GlobalObjectMappers;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@CommandLine.Command(name = "listMetrics",
        sortOptions = false,
        separator = " ",
        description = "List available metrics")
public class CommandPaListMetrics extends ACommandMiXCR {
    @Parameters(description = "Input file with PA results", index = "0")
    public String in;

    @Override
    public void run0() {
        PaResult paResult;
        try {
            paResult = GlobalObjectMappers.PRETTY.readValue(new File(in), PaResult.class);
        } catch (IOException e) {
            throwValidationException("Corrupted PA file.");
            throw new RuntimeException();
        }

        PaResultByGroup result = paResult.results.get(0);
        CharacteristicGroup<Clone, ?>
                biophys = result.schema.getGroup(CommandPaIndividual.Biophysics),
                diversity = result.schema.getGroup(CommandPaIndividual.Diversity);

        for (int i = 0; i < 2; i++) {
            System.out.println();
            CharacteristicGroup<Clone, ?> gr = i == 0 ? biophys : diversity;
            if (i == 0)
                System.out.println("Biophysics metrics:");
            else
                System.out.println("Diversity metrics:");
            result.result.forGroup(gr)
                    .data.values().stream()
                    .flatMap(d -> d.data.values()
                            .stream().flatMap(ma -> Arrays.stream(ma.data)))
                    .map(m -> m.key)
                    .distinct()
                    .forEach(metric -> System.out.println("    " + metric));
        }
    }
}
