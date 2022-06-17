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
package com.milaboratory.mixcr.tags;

// public class DropletCloneGraphParameters {
//     @JsonProperty("tagCliqueScorePower")
//     public final double tagCliqueScorePower;
//     @JsonProperty("cloneCliqueScorePower")
//     public final double cloneCliqueScorePower;
//     @JsonProperty("filter")
//     public final CloneTagTupleFilter filter;
//     @JsonProperty("maxTagCountRatio")
//     public final double maxTagCountRatio;
//
//     @JsonCreator
//     public DropletCloneGraphParameters(@JsonProperty("tagCliqueScorePower") double tagCliqueScorePower,
//                                        @JsonProperty("cloneCliqueScorePower") double cloneCliqueScorePower,
//                                        @JsonProperty("filter") CloneTagTupleFilter filter,
//                                        @JsonProperty("maxTagCountRatio") double maxTagCountRatio) {
//         this.tagCliqueScorePower = tagCliqueScorePower;
//         this.cloneCliqueScorePower = cloneCliqueScorePower;
//         this.filter = filter;
//         this.maxTagCountRatio = maxTagCountRatio;
//     }
//
//     @Override
//     public boolean equals(Object o) {
//         if (this == o) return true;
//         if (o == null || getClass() != o.getClass()) return false;
//         DropletCloneGraphParameters that = (DropletCloneGraphParameters) o;
//         return Double.compare(that.tagCliqueScorePower, tagCliqueScorePower) == 0 &&
//                 Double.compare(that.cloneCliqueScorePower, cloneCliqueScorePower) == 0 &&
//                 Objects.equals(filter, that.filter);
//     }
//
//     @Override
//     public int hashCode() {
//         return Objects.hash(tagCliqueScorePower, cloneCliqueScorePower, filter);
//     }
//
//     public static DropletCloneGraphParameters getDefault() {
//         try {
//             return GlobalObjectMappers.ONE_LINE.readValue(CloneTagTupleFilter.class.getClassLoader().getResourceAsStream("parameters/droplet_clone_graph_parameters.json"), DropletCloneGraphParameters.class);
//         } catch (IOException e) {
//             throw new RuntimeException(e);
//         }
//     }
// }
