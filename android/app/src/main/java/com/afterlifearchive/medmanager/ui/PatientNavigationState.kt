package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class PatientNavigationState(
    initialTab: PatientTab = PatientTab.TODAY,
    initialLoadedTabs: Set<PatientTab> = setOf(PatientTab.TODAY),
    initialSelectedDoseKey: String? = null,
) {
    var tab by mutableStateOf(initialTab)
        private set
    var loadedTabs by mutableStateOf(initialLoadedTabs + initialTab)
        private set
    var selectedDoseKey by mutableStateOf(initialSelectedDoseKey)
        private set

    fun selectTab(tab: PatientTab) {
        this.tab = tab
        loadedTabs = loadedTabs + tab
    }

    fun showDose(key: String) {
        selectedDoseKey = key
    }

    fun dismissDose() {
        selectedDoseKey = null
    }

    companion object {
        val Saver: Saver<PatientNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.tab.name,
                    state.loadedTabs.joinToString(",") { it.name },
                    state.selectedDoseKey.orEmpty(),
                )
            },
            restore = { saved ->
                val tab = saved[0].toString().toPatientTabOrDefault()
                val loaded = saved[1].toString().split(',')
                    .mapNotNull { name -> PatientTab.entries.firstOrNull { it.name == name } }
                    .toSet()
                PatientNavigationState(
                    initialTab = tab,
                    initialLoadedTabs = loaded,
                    initialSelectedDoseKey = saved[2].toString().takeIf(String::isNotBlank),
                )
            },
        )
    }
}

@Composable
internal fun rememberPatientNavigationState(): PatientNavigationState =
    rememberSaveable(saver = PatientNavigationState.Saver) { PatientNavigationState() }

private fun String.toPatientTabOrDefault() = PatientTab.entries.firstOrNull { it.name == this } ?: PatientTab.TODAY
