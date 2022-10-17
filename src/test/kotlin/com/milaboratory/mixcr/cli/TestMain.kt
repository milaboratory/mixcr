package com.milaboratory.mixcr.cli

import io.kotest.matchers.shouldBe

object TestMain {
    fun execute(args: String) {
        Main.mkCmd().execute(*args.split(" ").toTypedArray()) shouldBe 0
    }
}
