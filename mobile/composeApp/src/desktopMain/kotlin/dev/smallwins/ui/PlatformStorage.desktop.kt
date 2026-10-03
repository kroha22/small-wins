package dev.smallwins.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class MemoryKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

@Composable
internal actual fun rememberPlatformKeyValueStorage(): KeyValueStorage = remember { MemoryKeyValueStorage() }
