package com.afterlifearchive.medmanager.data.push

import android.content.Context
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class CaregiverPushState(
    val enabled: Boolean = false,
    val syncing: Boolean = false,
    val registered: Boolean = false,
    val configurationMissing: Boolean = false,
    val syncFailed: Boolean = false,
)

interface CaregiverPushDataSource {
    suspend fun register(token: String)
    suspend fun unregister(token: String)
}

class CaregiverPushApi(
    private val client: ApiClient,
    private val environment: String,
) : CaregiverPushDataSource {
    override suspend fun register(token: String) {
        client.post(
            "api/push/register",
            JSONObject()
                .put("token", token)
                .put("platform", "android")
                .put("environment", environment),
            RequestAuthPolicy.CAREGIVER,
        )
    }

    override suspend fun unregister(token: String) {
        client.post(
            "api/push/unregister",
            JSONObject().put("token", token),
            RequestAuthPolicy.CAREGIVER,
        )
    }
}

interface CaregiverPushTokenSource {
    val configured: Boolean
    fun setAutoInitEnabled(enabled: Boolean)
    suspend fun token(): String
}

interface CaregiverPushStorage {
    var enabled: Boolean
    var token: String?
    var registeredToken: String?
    var pendingUnregisterToken: String?
}

class AndroidCaregiverPushStorage(context: Context) : CaregiverPushStorage {
    private val preferences = context.getSharedPreferences("caregiver_push", Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
    override var token: String?
        get() = preferences.getString("token", null)
        set(value) { preferences.edit().putNullableString("token", value).apply() }
    override var registeredToken: String?
        get() = preferences.getString("registered_token", null)
        set(value) { preferences.edit().putNullableString("registered_token", value).apply() }
    override var pendingUnregisterToken: String?
        get() = preferences.getString("pending_unregister_token", null)
        set(value) { preferences.edit().putNullableString("pending_unregister_token", value).apply() }

    private fun android.content.SharedPreferences.Editor.putNullableString(key: String, value: String?) =
        if (value == null) remove(key) else putString(key, value)
}

class CaregiverPushRepository(
    private val dataSource: CaregiverPushDataSource,
    private val tokenSource: CaregiverPushTokenSource,
    private val storage: CaregiverPushStorage,
) {
    private val mutableState = MutableStateFlow(
        CaregiverPushState(
            enabled = storage.enabled,
            registered = storage.enabled && storage.registeredToken != null,
            configurationMissing = storage.enabled && !tokenSource.configured,
        ),
    )
    val state: StateFlow<CaregiverPushState> = mutableState.asStateFlow()

    fun acceptsMessages(): Boolean = storage.enabled

    suspend fun enable(): Boolean {
        storage.enabled = true
        mutableState.value = mutableState.value.copy(enabled = true, syncFailed = false)
        if (!tokenSource.configured) {
            mutableState.value = mutableState.value.copy(configurationMissing = true, registered = false)
            return false
        }
        tokenSource.setAutoInitEnabled(true)
        return synchronize()
    }

    suspend fun disable(): Boolean {
        storage.enabled = false
        tokenSource.setAutoInitEnabled(false)
        val registered = storage.registeredToken
        val token = registered ?: storage.token
        mutableState.value = CaregiverPushState(enabled = false, syncing = token != null)
        if (token == null) return true
        storage.pendingUnregisterToken = token
        return try {
            dataSource.unregister(token)
            storage.registeredToken = null
            storage.pendingUnregisterToken = null
            mutableState.value = CaregiverPushState(enabled = false)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = CaregiverPushState(enabled = false, syncFailed = true)
            false
        }
    }

    suspend fun retry(): Boolean = if (storage.enabled) synchronize() else retryPendingUnregister()

    suspend fun restoreIfEnabled(): Boolean {
        if (!storage.enabled) return retryPendingUnregister()
        if (!tokenSource.configured) {
            mutableState.value = mutableState.value.copy(configurationMissing = true, registered = false)
            return false
        }
        tokenSource.setAutoInitEnabled(true)
        return synchronize()
    }

    suspend fun onNewToken(token: String): Boolean {
        storage.token = token
        if (!storage.enabled) return false
        return register(token)
    }

    fun clearAfterAccountDeletion() {
        storage.enabled = false
        storage.token = null
        storage.registeredToken = null
        storage.pendingUnregisterToken = null
        tokenSource.setAutoInitEnabled(false)
        mutableState.value = CaregiverPushState()
    }

    private suspend fun synchronize(): Boolean {
        mutableState.value = mutableState.value.copy(
            syncing = true,
            configurationMissing = false,
            syncFailed = false,
        )
        return try {
            storage.pendingUnregisterToken?.let { staleToken ->
                dataSource.unregister(staleToken)
                storage.pendingUnregisterToken = null
                if (storage.registeredToken == staleToken) storage.registeredToken = null
            }
            register(tokenSource.token())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(syncing = false, registered = false, syncFailed = true)
            false
        }
    }

    private suspend fun register(token: String): Boolean = try {
        storage.token = token
        dataSource.register(token)
        storage.registeredToken = token
        mutableState.value = mutableState.value.copy(syncing = false, registered = true, syncFailed = false)
        true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        mutableState.value = mutableState.value.copy(syncing = false, registered = false, syncFailed = true)
        false
    }

    private suspend fun retryPendingUnregister(): Boolean {
        val token = storage.pendingUnregisterToken ?: return true
        mutableState.value = mutableState.value.copy(syncing = true, syncFailed = false)
        return try {
            dataSource.unregister(token)
            storage.pendingUnregisterToken = null
            if (storage.registeredToken == token) storage.registeredToken = null
            mutableState.value = CaregiverPushState(enabled = false)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = CaregiverPushState(enabled = false, syncFailed = true)
            false
        }
    }
}
