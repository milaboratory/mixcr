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

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;

//TODO documentation
public class LociLibrary implements AlleleResolver {
    final SequenceBase base = new SequenceBase();
    final List<Allele> allAlleles = new ArrayList<>();
    final Map<String, Integer> knownSpecies = new HashMap<>();
    final Map<String, String> properties = new HashMap<>();
    final Map<Integer, Map<String, Allele>> alleles = new HashMap<>();
    final Map<Integer, Map<String, Gene>> genes = new HashMap<>();
    final Map<SpeciesAndLocus, LocusContainer> loci = new HashMap<>();

    public void registerContainer(LocusContainer container) {
        if (loci.containsKey(container.getSpeciesAndLocus()))
            throw new IllegalArgumentException("This species/locus combination already registered. (" + container.getSpeciesAndLocus() + ")");

        loci.put(container.getSpeciesAndLocus(), container);
        container.setLibrary(this);

        Map<String, Allele> am = alleles.get(container.getSpeciesAndLocus().taxonId);
        if (am == null)
            alleles.put(container.getSpeciesAndLocus().taxonId, am = new HashMap<>());
        am.putAll(container.nameToAllele);
        allAlleles.addAll(container.getAllAlleles());

        Map<String, Gene> gm = genes.get(container.getSpeciesAndLocus().taxonId);
        if (gm == null)
            genes.put(container.getSpeciesAndLocus().taxonId, gm = new HashMap<>());
        gm.putAll(container.nameToGene);
    }

    public Map<SpeciesAndLocus, LocusContainer> getLociMap() {
        return unmodifiableMap(loci);
    }

    public Collection<LocusContainer> getLoci() {
        return unmodifiableCollection(loci.values());
    }

    public LocusContainer getLocus(String species, Locus locus) {
        Integer taxonId = knownSpecies.get(species);

        if (taxonId == null)
            taxonId = Species.fromString(species);

        if (taxonId == null || taxonId == -1)
            return null;

        return getLocus(taxonId, locus);
    }

    @Override
    public Allele getAllele(AlleleId id) {
        final LocusContainer locusContainer = loci.get(id.getSpeciesAndLocus());

        if (locusContainer == null)
            throw new IllegalArgumentException("No container for " + id.getSpeciesAndLocus());

        return locusContainer.getAllele(id);
    }

    public LocusContainer getLocus(int taxonId, Locus locus) {
        return loci.get(new SpeciesAndLocus(taxonId, locus));
    }

    public LocusContainer getLocus(SpeciesAndLocus speciesAndLocus) {
        return loci.get(speciesAndLocus);
    }

    public String getProperty(String name) {
        return properties.get(name);
    }

    public Allele getAllele(int species, String name) {
        return alleles.get(species).get(name);
    }

    public Gene getGene(int species, String name) {
        return genes.get(species).get(name);
    }

    public int getSpeciesTaxonId(String commonName) {
        Integer id = knownSpecies.get(commonName);
        if (id == null)
            return -1;
        return id;
    }

    public Collection<Allele> getAllAlleles(int species) {
        return Collections.unmodifiableCollection(alleles.get(species).values());
    }

    public Collection<Allele> getAllAlleles() {
        return Collections.unmodifiableCollection(allAlleles);
    }

    public Collection<Gene> getAllGenes(int species) {
        return Collections.unmodifiableCollection(genes.get(species).values());
    }

    public SequenceBase getBase() {
        return base;
    }
}
