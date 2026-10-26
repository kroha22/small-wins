package dev.smallwins.puzzle.core

@JsModule("fs")
@JsNonModule
private external object NodeFs {
    fun readFileSync(path: String, encoding: String): String

    fun existsSync(path: String): Boolean
}

actual fun readConformanceFixture(name: String): String {
    val candidates = listOf("conformance/$name", "../../../../conformance/$name")
    val path = candidates.firstOrNull(NodeFs::existsSync) ?: candidates.first()
    return NodeFs.readFileSync(path, "utf8")
}
