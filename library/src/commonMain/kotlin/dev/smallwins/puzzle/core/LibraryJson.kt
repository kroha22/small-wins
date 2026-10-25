package dev.smallwins.puzzle.core

import kotlinx.serialization.json.Json

val libraryJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
