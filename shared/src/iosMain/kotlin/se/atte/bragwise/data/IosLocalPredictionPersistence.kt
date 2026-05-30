package se.atte.bragwise.data

import platform.Foundation.NSUserDefaults

private const val KEY_BLOB = "bragwise.local_predictions.blob"

class IosLocalPredictionPersistence : LocalPredictionPersistence {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): String? = defaults.stringForKey(KEY_BLOB)

    override fun save(json: String?) {
        if (json == null) defaults.removeObjectForKey(KEY_BLOB)
        else defaults.setObject(json, KEY_BLOB)
    }
}
