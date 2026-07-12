package com.afterlifearchive.medmanager.data.session

import com.afterlifearchive.medmanager.ui.AppMode

interface SessionStorage {
    var mode: AppMode?
    var currentPatientId: String?
    fun getSecret(key: String): String?
    fun putSecret(key: String, value: String?)
    fun clearSecrets(keys: Iterable<String>) = keys.forEach { putSecret(it, null) }
}
