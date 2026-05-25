package org.acoustixaudio.casttobrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.castUsageDataStore by preferencesDataStore(name = "cast_usage")

class CastUsageRepository(private val context: Context) {
    private val castCountKey = intPreferencesKey("cast_count")
    private val paywallShownKey = booleanPreferencesKey("paywall_shown")

    suspend fun incrementCastCount(): Int {
        var updatedCount = 0
        context.castUsageDataStore.edit { preferences ->
            updatedCount = (preferences[castCountKey] ?: 0) + 1
            preferences[castCountKey] = updatedCount
        }
        return updatedCount
    }

    suspend fun hasShownPaywall(): Boolean {
        return context.castUsageDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[paywallShownKey] ?: false
    }

    suspend fun markPaywallShown() {
        context.castUsageDataStore.edit { preferences ->
            preferences[paywallShownKey] = true
        }
    }
}

