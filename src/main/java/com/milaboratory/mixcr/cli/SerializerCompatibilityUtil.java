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
package com.milaboratory.mixcr.cli;

public final class SerializerCompatibilityUtil {
    private SerializerCompatibilityUtil() {
    }

    // private static final HashMap<String, String> v3_0_3_Table = new HashMap<>();
    //
    // static {
    //     v3_0_3_Table.put("\"align-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandAlign$AlignConfiguration");
    //     v3_0_3_Table.put("\"assemble-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandAssemble$AssembleConfiguration");
    //     v3_0_3_Table.put("\"assemble-contig-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandAssembleContigs$AssembleContigsConfiguration");
    //     v3_0_3_Table.put("\"assemble-partial-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandAssemblePartialAlignments$AssemblePartialConfiguration");
    //     v3_0_3_Table.put("\"extend-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandExtend$ExtendConfiguration");
    //     v3_0_3_Table.put("\"merge-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandMergeAlignments$MergeConfiguration");
    //     v3_0_3_Table.put("\"filter-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandFilterAlignments$FilterConfiguration");
    //     v3_0_3_Table.put("\"sort-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandSortAlignments$SortConfiguration");
    //     v3_0_3_Table.put("\"slice-configuration\"",
    //             "com.milaboratory.mixcr.cli.CommandSlice$SliceConfiguration");
    // }
    //
    // public static void add_v3_0_3_CustomSerializers(PrimitivI input) {
    //     input.getSerializersManager().registerCustomSerializer(AppVersionInfo.class,
    //             new JSONSerializer(AppVersionInfo.class, s -> {
    //                 try {
    //                     JsonNode jsonNode = GlobalObjectMappers.ONE_LINE.readTree(s);
    //                     ObjectNode componentVersions = GlobalObjectMappers.ONE_LINE.createObjectNode();
    //                     ((ObjectNode) jsonNode).set("componentVersions", componentVersions);
    //                     componentVersions.set("mixcr", ((ObjectNode) jsonNode).remove("mixcr"));
    //                     componentVersions.set("milib", ((ObjectNode) jsonNode).remove("milib"));
    //                     componentVersions.set("repseqio", ((ObjectNode) jsonNode).remove("repseqio"));
    //                     ObjectNode componentStringVersions = GlobalObjectMappers.ONE_LINE.createObjectNode();
    //                     ((ObjectNode) jsonNode).set("componentStringVersions", componentStringVersions);
    //                     componentStringVersions.set("builtInLibrary", ((ObjectNode) jsonNode)
    //                             .remove("builtInLibrary"));
    //                     ((ObjectNode) jsonNode).set("type",
    //                             new TextNode("com.milaboratory.cli.AppVersionInfo"));
    //                     return jsonNode.toString();
    //                 } catch (IOException e) {
    //                     throw new RuntimeException(e);
    //                 }
    //             }));
    //     input.getSerializersManager().registerCustomSerializer(ActionConfiguration.class,
    //             new JSONSerializer(ActionConfiguration.class, s -> {
    //                 try {
    //                     JsonNode jsonNode = GlobalObjectMappers.ONE_LINE.readTree(s);
    //                     String typeName = jsonNode.get("type").toString();
    //                     if (v3_0_3_Table.containsKey(typeName))
    //                         ((ObjectNode) jsonNode).set("type", new TextNode(v3_0_3_Table.get(typeName)));
    //                     return jsonNode.toString();
    //                 } catch (IOException e) {
    //                     throw new RuntimeException(e);
    //                 }
    //             }));
    // }
}
