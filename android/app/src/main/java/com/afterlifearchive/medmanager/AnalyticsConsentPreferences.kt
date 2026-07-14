package com.afterlifearchive.medmanager

import android.content.Context

/** Stores explicit privacy consent. Firebase wiring consumes this flag in Gate H. */
class AnalyticsConsentPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("analytics_consent", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "collection_enabled"
    }
}
