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
package com.milaboratory.mixcr.cli.qc;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "qc",
        separator = " ",
        description = "Export QC plots.",
        subcommands = {
                CommandLine.HelpCommand.class
        })
public class CommandExportQc {}
