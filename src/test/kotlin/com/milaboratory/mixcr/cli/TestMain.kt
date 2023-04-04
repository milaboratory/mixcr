package com.milaboratory.mixcr.cli

import com.milaboratory.milm.MiXCRMain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

object TestMain {
    init {
        if (MiXCRMain.lm == null) {
            MiXCRMain.main()
        }
        Main.initializeSystem()
    }

    fun execute(args: String) {
        Main.mkCmd().execute(*args.split(" ").toTypedArray()) shouldBe 0
    }

    fun shouldFail(args: String) {
        Main.mkCmd().execute(*args.split(" ").toTypedArray()) shouldNotBe 0
    }
}
