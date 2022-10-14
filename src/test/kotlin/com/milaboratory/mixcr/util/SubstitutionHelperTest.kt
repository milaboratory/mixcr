package com.milaboratory.mixcr.util

import org.junit.Assert
import org.junit.Test

class SubstitutionHelperTest {
    @Test
    fun test1() {
        Assert.assertEquals(
            "export_AA_BB.txt",
            SubstitutionHelper.parseFileName("export.txt", 2)
                .render(
                    SubstitutionHelper.SubstitutionValues()
                        .add("AA", "1", "my_key_1")
                        .add("BB", "2", "my_key_2")
                )
        )
    }

    @Test
    fun test2() {
        Assert.assertEquals(
            "export_AA_BB",
            SubstitutionHelper.parseFileName("export", 2)
                .render(
                    SubstitutionHelper.SubstitutionValues()
                        .add("AA", "1", "my_key_1")
                        .add("BB", "2", "my_key_2")
                )
        )
    }

    @Test
    fun test3() {
        Assert.assertEquals(
            "export_AA_BB.txt",
            SubstitutionHelper.parseFileName("export_{{1}}_{{my_key_2}}.txt", 2)
                .render(
                    SubstitutionHelper.SubstitutionValues()
                        .add("AA", "1", "my_key_1")
                        .add("BB", "2", "my_key_2")
                )
        )
    }

    @Test
    fun test4() {
        Assert.assertEquals(
            "export_AA_BB",
            SubstitutionHelper.parseFileName("export_{{1}}_{{my_key_2}}", 2)
                .render(
                    SubstitutionHelper.SubstitutionValues()
                        .add("AA", "1", "my_key_1")
                        .add("BB", "2", "my_key_2")
                )
        )
    }

    @Test
    fun test5() {
        Assert.assertEquals(
            "export",
            SubstitutionHelper.parseFileName("export", 0)
                .render(SubstitutionHelper.SubstitutionValues())
        )
    }
}