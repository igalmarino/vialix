package com.galmarino.vialix.places

import android.content.Context
import androidx.core.content.edit

/** [KeyValueStore] over one key of a private `SharedPreferences` file. */
class SharedPreferencesKeyValueStore(context: Context, prefsName: String, private val key: String) : KeyValueStore {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(key, null)

    override fun write(value: String) = prefs.edit { putString(key, value) }
}
