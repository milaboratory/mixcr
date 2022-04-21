package com.milaboratory.mixcr.alleles;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.alleles.CommonMutationsSearcher.CloneDescription;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class CommonMutationsSearcherTest {
    private int counter = 0;
    private final AffineGapAlignmentScoring<NucleotideSequence> scoring = AffineGapAlignmentScoring.getNucleotideBLASTScoring();

    @Test
    public void notEnoughDiversityToFindAlleles() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST1G,ST3G", "J1*00"),
                clone("ST1G,ST2G,ST4G", "J1*00"),
                clone("ST1G,ST2G,ST5G,ST6G", "J2*00")
        ));
        assertEqualsMutations(result, "[]");
    }

    @Test
    public void oneMutationsExistsInAllCloneButOne() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST1G,ST3G", "J1*00"),
                clone("ST1G,ST2G,ST4G", "J1*00"),
                clone("ST1G,ST2G,ST5G"),
                clone("ST1G,ST2G,ST7G"),
                clone("ST1G,ST2G,ST8G"),
                clone("ST1G,ST2G,ST9G")
        ));
        assertEqualsMutations(result, "[S1:T->G,S2:T->G]");
    }

    @Test
    public void oneMutationsExistsInAllCloneInCaseOfSmallDiversity() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST1G,ST3G", "J1*00"),
                clone("ST1G,ST4G", "J1*00"),
                clone("ST1G,ST5G,ST6G", "J2*00"),
                clone("ST1G,ST5G,ST6G", "J3*00")
        ));
        assertEqualsMutations(result, "[S1:T->G]");
    }

    @Test
    public void onlyOneAlleleThatIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone(""),
                clone("ST1G"),
                clone(""),
                clone(""),
                clone("ST3G")
        ));
        assertEqualsMutations(result, "[]");
    }

    @Test
    public void onlyOneAlleleThatIsTheSameAsLibraryGermlineAndAllOthersInTheSameTree() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone(""),
                clone(""),
                clone(""),
                clone("ST1G"),
                clone("ST2G"),
                clone("ST3G,ST4G", "J2*00"),
                clone("ST3G,ST5G", "J2*00"),
                clone("ST3G,ST4G,ST6G", "J2*00"),
                clone("ST3G,ST4G,ST7G", "J2*00"),
                clone("ST3G,ST4G,ST8G", "J2*00"),
                clone("ST3G,ST4G,ST9G", "J2*00")
        ));
        assertEqualsMutations(result, "[]");
    }

    @Test
    public void allelesThatSpreadInClustersEvenly() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST1G", "J1*00"),
                clone("ST3G,ST4G", "J1*00"),
                clone("ST1G", "J2*00"),
                clone("ST3G,ST4G", "J2*00"),
                clone("ST1G", "J3*00"),
                clone("ST3G,ST4G", "J3*00"),
                clone("ST1G", "J4*00"),
                clone("ST3G,ST4G", "J4*00"),
                clone("ST1G", "J5*00"),
                clone("ST3G,ST4G", "J5*00")
        ));
        assertEqualsMutations(result, "[S1:T->G]", "[S3:T->G,S4:T->G]");
    }

    @Test
    public void allelesThatSpreadInClustersEvenlyAndHaveOverlap() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST1G,ST3G", "J1*00"),
                clone("ST3G,ST4G", "J1*00"),
                clone("ST1G,ST3G", "J2*00"),
                clone("ST3G,ST4G", "J2*00"),
                clone("ST1G,ST3G", "J3*00"),
                clone("ST3G,ST4G", "J3*00"),
                clone("ST1G,ST3G", "J4*00"),
                clone("ST3G,ST4G", "J4*00"),
                clone("ST1G,ST3G", "J5*00"),
                clone("ST3G,ST4G", "J5*00")
        ));
        assertEqualsMutations(result, "[S1:T->G,S3:T->G]", "[S3:T->G,S4:T->G]");
    }

    @Test
    public void onlyOneAlleleThatDifferentFromLibraryGermlineAndAllOthersInTheSameTree() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone(""),
                clone("ST3G,ST4G", "J2*00"),
                clone("ST3G,ST5G", "J2*00"),
                clone("ST3G,ST4G,ST6G", "J2*00"),
                clone("ST3G,ST4G,ST7G", "J2*00"),
                clone("ST3G,ST4G,ST8G", "J2*00"),
                clone("ST3G,ST4G,ST9G", "J2*00")
        ));
        assertEqualsMutations(result, "[]", "[S0:A->G]");
    }

    @Test
    public void onlyOneAlleleThatHaveOneMutation() {
        CommonMutationsSearcher searcher = searcher("ATTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G"),
                clone("SA0G"),
                clone("SA0G,ST3G")
        ));
        assertEqualsMutations(result, "[S0:A->G]");
    }

    @Test
    public void onlyOneAlleleThatHaveSeveralMutations() {
        CommonMutationsSearcher searcher = searcher("ATTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G,ST3G")
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]");
    }

    @Test
    public void onlyOneAlleleThatHaveManyMutationsAndOneCloneHaveReversedMutations() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
        Function<Integer, CloneDescription> cloneFactory = size -> clone(IntStream.rangeClosed(0, size)
                .mapToObj(i -> "ST" + i + "G")
                .collect(Collectors.joining(",", "[", "]")));
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(9)
        ));
        assertEqualsMutations(result, IntStream.rangeClosed(0, 10)
                .mapToObj(i -> "S" + i + ":T->G")
                .collect(Collectors.joining(",", "[", "]"))
        );
    }

    @Test
    public void secondAlleleThatHaveManyMutationsAndOneCloneHaveReversedMutations() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
        Function<Integer, CloneDescription> cloneFactory = size -> clone(IntStream.rangeClosed(0, size)
                .mapToObj(i -> "ST" + i + "G")
                .collect(Collectors.joining(",", "[", "]")));
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(10),
                cloneFactory.apply(9)
        ));
        assertEqualsMutations(result, "[S15:T->G]", IntStream.rangeClosed(0, 10)
                .mapToObj(i -> "S" + i + ":T->G")
                .collect(Collectors.joining(",", "[", "]"))
        );
    }

    @Test
    public void secondAlleleThatHaveManyMutationsAndSeveralClonesHaveMoreMutations() {
        CommonMutationsSearcher searcher = searcher("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
        Function<Integer, CloneDescription> cloneFactory = size -> clone(IntStream.rangeClosed(0, size)
                .mapToObj(i -> "ST" + i + "G")
                .collect(Collectors.joining(",", "[", "]")));
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                clone("ST15G"),
                cloneFactory.apply(4),
                cloneFactory.apply(10),
                cloneFactory.apply(11),
                cloneFactory.apply(12),
                cloneFactory.apply(10),
                cloneFactory.apply(11),
                cloneFactory.apply(13),
                cloneFactory.apply(9)
        ));
        assertEqualsMutations(result, "[S15:T->G]", IntStream.rangeClosed(0, 4)
                .mapToObj(i -> "S" + i + ":T->G")
                .collect(Collectors.joining(",", "[", "]"))
        );
    }

    @Test
    public void oneAlleleWithOneMutationAndSecondIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G"),
                clone(""),
                clone(""),
                clone("ST3G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[]");
    }

    @Test
    public void oneAlleleWithOneMutationIsMoreRepresentedThanSecondThatIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST4G"),
                clone("SA0G,ST5G"),
                clone("SA0G,ST6G"),
                clone("SA0G,ST7G"),
                clone("SA0G,ST8G"),
                clone("SA0G,ST9G"),
                clone(""),
                clone(""),
                clone("ST3G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[]");
    }

    @Test
    public void chooseAlleleWithMoreMutations() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("ST1G,ST2G,ST3G"),
                clone("ST1G,ST2G,ST3G"),
                clone("ST1G,ST2G,ST3G"),
                clone("ST1G,ST2G,ST3G"),
                clone("ST1G,ST2G,ST3G"),
                clone("ST1G,ST2G,ST3G"),
                clone("ST1G,ST2G,ST3G"),
                clone(""),
                clone(""),
                clone("ST3G")
        ));
        assertEqualsMutations(result, "[]", "[S1:T->G,S2:T->G,S3:T->G]");
    }

    @Test
    public void oneAlleleWithoutMutationsIsMoreRepresentedThanSecondThatHaveOneMutation() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone(""),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST4G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[]");
    }

    @Test
    public void oneAlleleWithoutMutationsIsMoreRepresentedThanSecondThatHaveOneMutationAndOneCloneWithMutationIsNotFromSecondAllele() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTT");
        List<CloneDescription> clones = new ArrayList<>();
        IntStream.range(0, 20).forEach(i -> clones.add(clone("")));
        IntStream.range(0, 10).forEach(i -> clones.add(clone("SA0G")));
        clones.add(clone("ST1G"));
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(clones);
        assertEqualsMutations(result, "[S0:A->G]", "[]");
    }

    @Test
    public void oneAlleleWithOneMutationIsMoreRepresentedThanSecondThatIsDifferentFromLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST4G"),
                clone("SA0G,ST5G"),
                clone("SA0G,ST6G"),
                clone("SA0G,ST7G"),
                clone("SA0G,ST8G"),
                clone("SA0G,ST9G"),
                clone("ST3G"),
                clone("ST3G"),
                clone("ST3G,ST10G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[S3:T->G]");
    }

    @Test
    public void oneMutationCoversAlmostAllClonesAndThisMutationWithSeveralOthersCoversAHalfButThereAreOtherClones() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone(""),
                clone("ST5G"),
                clone("ST10G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[]");
    }

    @Test
    public void oneMutationCoversAlmostAllClonesAndThisMutationWithSeveralOthersCoversAHalfButLeftClonesCanBeGrouped() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("ST3G"),
                clone("ST3G,ST5G"),
                clone("ST3G,ST10G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[S3:T->G]");
    }

    @Test
    public void oneMutationCoversAlmostAllClonesAndThisMutationWithSeveralOthersCoversAHalf() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SA0G,ST1G,ST2G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[S0:A->G,S1:T->G,S2:T->G]");
    }

    @Test
    public void oneMutationCoversAlmostAllClonesAndThisMutationWithSeveralOthersCoversAHalfButAlsoHaveRegularHupermutation() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTTTTTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G,ST2G,ST3G"),
                clone("SA0G,ST1G,ST2G,ST3G"),
                clone("SA0G,ST1G,ST2G,ST3G"),
                clone("SA0G,ST1G,ST2G,ST3G"),
                clone("SA0G,ST1G,ST2G,ST4G"),
                clone("SA0G,ST1G,ST2G,ST4G"),
                clone("SA0G,ST1G,ST2G,ST4G"),
                clone("SA0G,ST1G,ST2G,ST4G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[S0:A->G,S1:T->G,S2:T->G]");
    }

    @Test
    public void oneAlleleWithSeveralMutationsAndSecondIsTheSameAsLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTTTTTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G,ST2G"),
                clone(""),
                clone(""),
                clone("ST3G")
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]", "[]");
    }

    @Test
    public void twoAllelesWithOneMutationDifferentFromLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTCTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G"),
                clone("SA0G"),
                clone("SA0G,ST1G"),
                clone("SC3G"),
                clone("SC3G"),
                clone("SC3G,ST4G")
        ));
        assertEqualsMutations(result, "[S0:A->G]", "[S3:C->G]");
    }

    @Test
    public void twoAllelesWithSeveralMutationsDifferentFromLibraryGermline() {
        CommonMutationsSearcher searcher = searcher("ATTCATTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G,ST2G"),
                clone("SC3G,SA4G"),
                clone("SC3G,SA4G"),
                clone("SC3G,SA4G,ST5G")
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]", "[S3:C->G,S4:A->G]");
    }

    @Test
    public void twoAllelesThatHaveCommonMutation() {
        CommonMutationsSearcher searcher = searcher("ATTCTTTTT");
        List<Mutations<NucleotideSequence>> result = searcher.findAlleles(Lists.newArrayList(
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G"),
                clone("SA0G,ST1G,ST2G"),
                clone("ST1G,SC3G"),
                clone("ST1G,SC3G"),
                clone("ST1G,SC3G,ST4G")
        ));
        assertEqualsMutations(result, "[S0:A->G,S1:T->G]", "[S1:T->G,S3:C->G]");
    }

    private CommonMutationsSearcher searcher(String sequence1) {
        var parameters = new FindAllelesParameters(
                2,
                3,
                5,
                0.8,
                2.0,
                true,
                0.9
        );
        return new CommonMutationsSearcher(parameters, scoring, new NucleotideSequence(sequence1));
    }

    private void assertEqualsMutations(List<Mutations<NucleotideSequence>> result, String... mutations) {
        assertEquals(
                Sets.newHashSet(mutations),
                result.stream().map(Mutations::toString).collect(Collectors.toSet())
        );
    }

    private CloneDescription clone(String mutations, String complementaryGeneName) {
        return clone(mutations, 12, complementaryGeneName);
    }

    private CloneDescription clone(String mutations, int cdr3Length, String complementaryGeneName) {
        return new CloneDescription(
                () -> IntStream.of(new Mutations<>(NucleotideSequence.ALPHABET, mutations).getRAWMutations()),
                cdr3Length,
                complementaryGeneName
        );
    }

    private CloneDescription clone(String mutations) {
        return clone(mutations, 12, "J" + counter++);
    }
}
