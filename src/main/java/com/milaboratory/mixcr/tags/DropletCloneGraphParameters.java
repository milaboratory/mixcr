/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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
