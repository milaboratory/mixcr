/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.reference;

import java.util.*;

/**
 * Container of information about single locus.
 */
public class LocusContainer {
    final Map<String, String> properties = new HashMap<>();
    final EnumMap<GeneType, List<Gene>> genes;
    final EnumMap<GeneType, List<Allele>> alleles, referenceAlleles = new EnumMap<>(GeneType.class);
    final Map<String, Gene> nameToGene;
    final Map<String, Allele> nameToAllele;
    final List<Gene> allGenes;
    private final UUID uuid;
    private final SpeciesAndLocus speciesAndLocus;
    LociLibrary library = null;

    public LocusContainer(UUID uuid, SpeciesAndLocus speciesAndLocus, EnumMap<GeneType, List<Gene>> genes, EnumMap<GeneType, List<Allele>> alleles,
                          Map<String, Gene> nameToGene, Map<String, Allele> nameToAllele, List<Gene> allGenes) {
        this.uuid = uuid;
        this.speciesAndLocus = speciesAndLocus;
        this.genes = genes;
        this.alleles = alleles;
        this.nameToGene = nameToGene;
        this.nameToAllele = nameToAllele;
        this.allGenes = allGenes;
    }

    public LociLibrary getLibrary() {
        return library;
    }

    void setLibrary(LociLibrary library) {
        if (this.library != null && this.library != library)
            throw new RuntimeException();

        this.library = library;
    }

    public Allele getAllele(AlleleId id) {
        if (!id.getContainerUUID().equals(uuid))
            throw new IllegalArgumentException("Allele from different container.");
        return nameToAllele.get(id.getName());
    }

    public String getProperty(String name) {
        return properties.get(name);
    }

    public List<Gene> getGenes(GeneType type) {
        return genes.get(type);
    }

    public List<Gene> getAllGenes() {
        return allGenes;
    }

    public Collection<Allele> getAllAlleles() {
        return nameToAllele.values();
    }

    public Gene getGene(GeneType type, int index) {
        return genes.get(type).get(index);
    }

    public Gene getGene(String name) {
        return nameToGene.get(name);
    }

    public List<Allele> getAlleles(GeneType type) {
        return alleles.get(type);
    }

    public List<Allele> getReferenceAlleles(GeneType type) {
        List<Allele> rAlleles = referenceAlleles.get(type);

        if (rAlleles == null)
            synchronized (this) {
                if ((rAlleles = referenceAlleles.get(type)) == null) {
                    rAlleles = new ArrayList<>();
                    for (Gene g : getGenes(type))
                        rAlleles.add(g.getReferenceAllele());
                    referenceAlleles.put(type, rAlleles);
                }
            }

        return rAlleles;
    }

    public Allele getAllele(GeneType type, int index) {
        return alleles.get(type).get(index);
    }

    public Allele getAllele(String name) {
        return nameToAllele.get(name);
    }

    public UUID getUUID() {
        return uuid;
    }

    public SpeciesAndLocus getSpeciesAndLocus() {
        return speciesAndLocus;
    }

    public Locus getLocus() {
        return speciesAndLocus.locus;
    }

    public boolean hasDGene() {
        return speciesAndLocus.locus.hasDGene;
    }
}
