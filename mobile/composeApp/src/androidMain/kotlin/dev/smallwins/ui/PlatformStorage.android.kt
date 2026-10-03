package dev.smallwins.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AndroidKeyValueStorage(context: Context) : KeyValueStorage {
    private val preferences = context.getSharedPreferences("small-wins", Context.MODE_PRIVATE)
    override fun read(key: String) = preferences.getString(key, null)
    override fun write(key: String, value: String) { preferences.edit().putString(key, value).apply() }
    override fun remove(key: String) { preferences.edit().remove(key).apply() }
}

@Composable
internal actual fun rememberPlatformKeyValueStorage(): KeyValueStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidKeyValueStorage(context) }
}
