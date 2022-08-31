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
package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import com.milaboratory.util.GlobalObjectMappers
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.File
import java.util.*
import java.util.List

@Command(name = "listMetrics",
        sortOptions = false,
        separator = " ",
        description = "List available metrics")
public class CommandPaListMetrics extends MiXCRCommand {
    @Parameters(description = "Input file with PA results", index = "0")
    public String in;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.emptyList();
    }

    @Override
    public void run0() {
        PaResult paResult;
        try {
            paResult = GlobalObjectMappers.getPretty().readValue(new File(in), PaResult.class);
        } catch (IOException e) {
            throwValidationException("Corrupted PA file.");
            throw new RuntimeException();
        }

        PaResultByGroup result = paResult.results.get(0);
        CharacteristicGroup<Clone, ?>
                biophys = result.schema.getGroup(PostanalysisParametersIndividual.CDR3Metrics),
                diversity = result.schema.getGroup(PostanalysisParametersIndividual.Diversity);

        for (int i = 0; i < 2; i++) {
            System.out.println();
            CharacteristicGroup<Clone, ?> gr = i == 0 ? biophys : diversity;
            if (i == 0)
                System.out.println("CDR3 metrics:");
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
