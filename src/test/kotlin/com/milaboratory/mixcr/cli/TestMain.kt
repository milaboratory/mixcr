package com.milaboratory.mixcr.cli

import com.milaboratory.milm.MiXCRMain
import io.kotest.matchers.shouldBe

object TestMain {
    init {
        if (MiXCRMain.lm == null) {
            MiXCRMain.main()
        }
    }

    fun execute(args: String) {
        Main.mkCmd().execute(*args.split(" ").toTypedArray()) shouldBe 0
    }
}
