package com.milaboratory.mixcr.cli

object TestMain {
    fun execute(args: String) {
        Main.mkCmd().execute(*args.split(" ").toTypedArray())
    }
}
