package com.milaboratory.mixcr.trees

class CloneOrFoundAncestor(
    private val id: Int,
    private val cloneInfo: CloneInfo?
) {
    class CloneInfo(
        private val id: Int,
        private val count: Int,
        private val CGeneName: String
    )
}
