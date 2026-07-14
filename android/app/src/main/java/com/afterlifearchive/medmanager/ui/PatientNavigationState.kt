package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.LocalDate

@Stable
internal class PatientNavigationState(
    initialTab: PatientTab = PatientTab.TODAY,
    initialLoadedTabs: Set<PatientTab> = setOf(PatientTab.TODAY),
    initialSelectedDoseKey: String? = null,
    initialSelectedHistoryDate: LocalDate? = null,
) {
    var tab by mutableStateOf(initialTab)
        private set
    var loadedTabs by mutableStateOf(initialLoadedTabs + initialTab)
        private set
    var selectedDoseKey by mutableStateOf(initialSelectedDoseKey)
        private set
    var selectedHistoryDate by mutableStateOf(initialSelectedHistoryDate)
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

    fun showHistoryDate(date: LocalDate) {
        selectedHistoryDate = date
    }

    fun dismissHistoryDate() {
        selectedHistoryDate = null
    }

    companion object {
        val Saver: Saver<PatientNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.tab.name,
                    state.loadedTabs.joinToString(",") { it.name },
                    state.selectedDoseKey.orEmpty(),
                    state.selectedHistoryDate?.toString().orEmpty(),
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
                    initialSelectedHistoryDate = saved[3].toString().takeIf(String::isNotBlank)?.let(LocalDate::parse),
                )
            },
        )
    }
}

@Composable
internal fun rememberPatientNavigationState(): PatientNavigationState =
    rememberSaveable(saver = PatientNavigationState.Saver) { PatientNavigationState() }

private fun String.toPatientTabOrDefault() = PatientTab.entries.firstOrNull { it.name == this } ?: PatientTab.TODAY
