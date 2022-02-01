package com.milaboratory.mixcr.alleles;

import com.google.common.collect.Sets;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class CommonMutationsSearcherTest {
    @Test
    public void onlyOneAlleleThatIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G",
                "",
                "ST1G",
                "",
                "",
                "ST3G"
        ));
        assertEqualsMutations(result, "[]");
    }

    @Test
    public void onlyOneAlleleThatHaveOneMutation() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G",
                "SA0G",
                "SA0G,ST1G",
                "SA0G",
                "",
                "SA0G,ST3G"
        ));
        assertEqualsMutations(result, "[S0:A->G]");
    }

    @Test
    public void onlyOneAlleleThatHaveSeveralMutations() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G,ST1G",
                "SA0G,ST1G",
                "SA0G,ST1G,SC2G",
                "SA0G,ST1G",
                "SA0G",
                "SA0G,ST1G,ST3G"
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]");
    }

    @Test
    public void oneAlleleWithOneMutationAndSecondIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G",
                "SA0G",
                "SA0G,ST1G",
                "",
                "",
                "ST3G"
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[]");
    }

    @Test
    public void oneAlleleWithSeveralMutationsAndSecondIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G,ST1G",
                "SA0G,ST1G",
                "SA0G,ST1G,SC2G",
                "",
                "",
                "ST3G"
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]", "[]");
    }

    @Test
    public void twoAllelesWithOneMutationDifferentFromLibraryGermline() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G",
                "SA0G",
                "SA0G,ST1G",
                "SC3G",
                "SC3G",
                "SC3G,SA4G"
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[S3:C->G]");
    }

    @Test
    public void twoAllelesWithSeveralMutationsDifferentFromLibraryGermline() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G,ST1G",
                "SA0G,ST1G",
                "SA0G,ST1G,SA1G",
                "SC3G,SA4G",
                "SC3G,SA4G",
                "SC3G,SA4G,ST5G"
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]", "[S3:C->G,S4:A->G]");
    }

    @Test
    public void twoAllelesThatHaveCommonMutation() {
        CommonMutationsSearcher searcher = new CommonMutationsSearcher(0.4);

        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones(
                "SA0G,ST1G",
                "SA0G,ST1G",
                "SA0G,ST1G,SA1G",
                "ST1G,SC3G",
                "ST1G,SC3G",
                "ST1G,SC3G,SA4G"
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]", "[S1:T->G,S3:C->G]");
    }

    private void assertEqualsMutations(List<Mutations<NucleotideSequence>> result, String... mutations) {
        assertEquals(
                Sets.newHashSet(mutations),
                result.stream().map(Mutations::toString).collect(Collectors.toSet())
        );
    }

    private List<Supplier<IntStream>> clones(String... mutations) {
        return Arrays.stream(mutations)
                .<Supplier<IntStream>>map(mutationDescription -> () ->
                        IntStream.of(new Mutations<>(NucleotideSequence.ALPHABET, mutationDescription).getRAWMutations())
                )
                .collect(Collectors.toList());
    }
}
