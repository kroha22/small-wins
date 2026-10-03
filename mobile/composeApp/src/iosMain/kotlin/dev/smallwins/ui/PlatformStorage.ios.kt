package dev.smallwins.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private class IosKeyValueStorage : KeyValueStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(key: String) = defaults.stringForKey(key)
    override fun write(key: String, value: String) { defaults.setObject(value, forKey = key) }
    override fun remove(key: String) { defaults.removeObjectForKey(key) }
}

@Composable
internal actual fun rememberPlatformKeyValueStorage(): KeyValueStorage = remember { IosKeyValueStorage() }
