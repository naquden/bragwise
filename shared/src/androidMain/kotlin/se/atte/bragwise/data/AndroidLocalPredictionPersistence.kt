package se.atte.bragwise.data

import android.content.Context

private const val PREFS = "bragwise_local_predictions"
private const val KEY_BLOB = "blob"

class AndroidLocalPredictionPersistence(context: Context) : LocalPredictionPersistence {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_BLOB, null)

    override fun save(json: String?) {
        prefs.edit().apply {
            if (json == null) remove(KEY_BLOB) else putString(KEY_BLOB, json)
        }.apply()
    }
}
