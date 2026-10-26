package dev.smallwins.puzzle.core

import java.io.File

actual fun readConformanceFixture(name: String): String = File("conformance/$name").readText()
