package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDataSource
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlinx.coroutines.delay

class PatientTodayContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersNextSlotInventoryPartialActionAndPrn() {
        lateinit var activity: Activity
        val medications = listOf(
            medication("enough", 10.0),
            medication("short", 0.5),
            medication("prn", 10.0, isPrn = true),
        ).associateBy(PatientMedication::id)
        var bulkSlot: MedicationSlot? = null
        var prnId: String? = null

        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(
                    Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding(),
                ) {
                    TodayContent(
                        doses = listOf(dose("enough", DoseStatus.PENDING), dose("short", DoseStatus.MISSED)),
                        loading = false,
                        updatingKey = null,
                        error = null,
                        message = null,
                        maintenanceWarning = null,
                        medications = medications,
                        nextSlot = MedicationSlot.MORNING,
                        updatingSlot = null,
                        prnMedications = listOf(medications.getValue("prn")),
                        updatingPrnMedicationId = null,
                        onRetry = {},
                        onRecord = {},
                        onDetail = {},
                        onRecordSlot = { bulkSlot = it },
                        onRecordPrn = { prnId = it.id },
                        onRemind = {},
                        now = Instant.parse("2026-07-13T23:15:00Z"),
                    )
                }
            }
        }

        composeRule.onNodeWithText("次に飲むお薬").assertIsDisplayed()
        composeRule.onAllNodesWithText("在庫不足のお薬が1件あります").onFirst().assertIsDisplayed()
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-inventory-partial-light.png")
        composeRule.onNodeWithText("この時間のお薬を飲んだ").performClick()
        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("飲んだ薬を選んでください").assertIsDisplayed()
        composeRule.onNodeWithText("頭痛薬 1錠").assertIsDisplayed()
        composeRule.onNodeWithText("1回1錠").assertIsDisplayed()
        composeRule.onNodeWithText("痛い時").assertIsDisplayed()
        capturePrnSheet("android-ui-103-prn-list-light.png")
        composeRule.onNodeWithTag("prn-record-prn").performClick()

        composeRule.runOnIdle {
            assertEquals(MedicationSlot.MORNING, bulkSlot)
            assertEquals("prn", prnId)
        }
    }

    @Test
    fun plannedDoseStatusesUseCurrentIosCopy() {
        showTodayState(
            doses = listOf(
                dose("taken", DoseStatus.TAKEN),
                dose("missed", DoseStatus.MISSED),
                dose("pending", DoseStatus.PENDING),
            ),
        )

        listOf("記録済み", "飲み忘れ", "未記録").forEach { status ->
            composeRule.onNodeWithTag("patient-today-list").performScrollToNode(hasText(status))
            composeRule.onNodeWithText(status).assertIsDisplayed()
        }
        listOf("服用済み", "未達", "未服用").forEach { staleStatus ->
            composeRule.onAllNodesWithText(staleStatus).assertCountEquals(0)
        }
    }

    @Test
    fun currentIosMatchedTodayFixtureRendersInDarkTheme() {
        val activity = showMatchedAdaptiveToday(darkTheme = true)

        composeRule.onNodeWithText("7月15日（水）").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-today-primary-bulk-record").assertIsDisplayed()
        composeRule.runOnIdle { normalizeStatusBar(activity, darkTheme = true) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-today-dark-matched.png")
    }

    @Test
    fun currentIosMatchedTodayFixtureRendersAtTwoHundredPercentFontScale() {
        val activity = showMatchedAdaptiveToday(fontScale = 2f)

        composeRule.onNodeWithText("7月15日（水）").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-today-primary-bulk-record").assertIsDisplayed()
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-today-font-2.0-matched.png")
    }

    @Test
    fun prnSheetShowsCurrentIosUpdatingOverlay() {
        showPrnSheet(updatingPrnMedicationId = "prn")

        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("patient-prn-updating").assertIsDisplayed()
        composeRule.onAllNodesWithText("更新中...").assertCountEquals(1)
        capturePrnSheet("android-ui-103-prn-updating-light.png")
    }

    @Test
    fun prnSheetKeepsRetryableFailureVisible() {
        showPrnSheet(prnError = "取得に失敗しました")

        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithText("取得に失敗しました").assertIsDisplayed()
        composeRule.onNodeWithText("この薬を飲んだ").assertIsDisplayed()
        capturePrnSheet("android-ui-103-prn-error-light.png")
    }

    @Test
    fun prnInsufficientInventoryShowsBadgeAndDisablesRecord() {
        var recordCount = 0
        showPrnSheet(
            prnQuantity = 0.5,
            onRecordPrn = { recordCount += 1 },
        )

        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithText("在庫不足").assertIsDisplayed()
        composeRule.onNodeWithTag("prn-record-prn").assertIsDisplayed().assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, recordCount) }
        capturePrnSheet("android-ui-103-prn-insufficient-light.png")
    }

    @Test
    fun prnSheetPreservesHierarchyInDarkTheme() {
        showPrnSheet(darkTheme = true)

        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithText("飲んだ薬を選んでください").assertIsDisplayed()
        composeRule.onNodeWithTag("prn-record-prn").assertIsDisplayed()
        capturePrnSheet("android-ui-103-prn-list-dark.png")
    }

    @Test
    fun prnRecordActionRemainsReachableAtTwoHundredPercentFontScale() {
        showPrnSheet(fontScale = 2f)

        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithText("頭痛薬 1錠").assertIsDisplayed()
        composeRule.onNodeWithTag("prn-record-prn").performScrollTo().assertIsDisplayed()
        capturePrnSheet("android-ui-103-prn-list-font-2.0.png")
    }

    @Test
    fun prnSheetClosesOnlyAfterSuccessfulRecordRevision() {
        var successRevision by mutableStateOf(0L)
        var clearCount = 0
        showPrnSheet(
            successRevision = { successRevision },
            onClearFeedback = { clearCount += 1 },
        )
        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithText("飲んだ薬を選んでください").assertIsDisplayed()

        composeRule.runOnIdle { successRevision = 1 }

        composeRule.onAllNodesWithText("飲んだ薬を選んでください").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(2, clearCount) }
    }

    @Test
    fun productionPatientHomeConfirmsRecordsAndClosesPrnFlow() {
        val prn = medication("prn", 10.0, isPrn = true)
        var recordCount = 0
        val repository = PatientRepository(object : PatientDataSource {
            override suspend fun today() = emptyList<PatientDose>()
            override suspend fun slotTimes() = PatientSlotTimes.DEFAULT
            override suspend fun medications() = listOf(prn)
            override suspend fun recordDose(dose: PatientDose) = Unit
            override suspend fun recordPrn(medication: PatientMedication) { recordCount += 1 }
            override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
            override suspend fun revokeSession() = Unit
        })
        composeRule.setContent {
            MedicationAppTheme {
                PatientHomeScreen(repository = repository, onUnlink = {}, tutorialEnabled = false)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.state.value.prnMedications.isNotEmpty() }

        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("prn-record-prn").performClick()
        composeRule.onNodeWithText("飲みましたか？").assertIsDisplayed()
        composeRule.onNodeWithText("頭痛薬を今飲みましたか？").assertIsDisplayed()
        composeRule.onNodeWithText("飲んだ").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { repository.state.value.prnRecordSuccessRevision == 1L }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("飲んだ薬を選んでください").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, recordCount) }
    }

    @Test
    fun todayShowsCurrentIosInitialLoadingAsFullState() {
        val (activity, repository) = showProductionInitialTodayState(fail = false)

        composeRule.waitUntil(timeoutMillis = 5_000) { repository.state.value.loading }
        composeRule.onNodeWithTag("patient-today-initial-loading").assertIsDisplayed()
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        composeRule.onAllNodesWithText("今日のお薬").assertCountEquals(0)
        composeRule.onNodeWithText("今日").assertIsDisplayed()
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-initial-loading-light-matched.png")
    }

    @Test
    fun todayShowsCurrentIosInitialFailureAsFullState() {
        val (activity, repository) = showProductionInitialTodayState(fail = true)

        composeRule.waitUntil(timeoutMillis = 5_000) { repository.state.value.error != null }
        composeRule.onNodeWithTag("patient-today-initial-error").assertIsDisplayed()
        composeRule.onNodeWithText("取得に失敗しました").assertIsDisplayed()
        composeRule.onAllNodesWithText("今日のお薬").assertCountEquals(0)
        composeRule.onNodeWithText("今日").assertIsDisplayed()
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-initial-error-light-matched.png")
    }

    @Test
    fun todayBlocksCachedContentBehindCurrentIosUpdatingOverlay() {
        showTodayState(
            refreshing = true,
            doses = listOf(dose("enough", DoseStatus.PENDING)),
        )

        composeRule.onNodeWithTag("patient-today-updating").assertIsDisplayed()
        composeRule.onNodeWithText("更新中...").assertIsDisplayed()
        composeRule.onAllNodesWithText("血圧の薬 5 mg", substring = true).onFirst().assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-101-patient-updating-light.png",
        )
    }

    @Test
    fun todayKeepsLongMedicationNamesBoundedWithoutHidingThePrimaryAction() {
        val longName = "朝食後に服用する非常に長い名称の血圧降下薬配合錠ジェネリック医薬品"
        val longDose = dose("long", DoseStatus.PENDING).copy(medicationName = longName)
        showTodayState(doses = listOf(longDose))

        composeRule.onAllNodesWithText(longName).onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("patient-today-primary-bulk-record").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-101-patient-long-name-light.png",
        )
    }

    @Test
    fun productionNotificationTargetPromotesExactSlotIntoNextDoseHero() {
        lateinit var activity: Activity
        val morning = dose("morning", DoseStatus.PENDING).copy(
            key = "dose-morning",
            slot = MedicationSlot.MORNING,
            scheduledAt = Instant.parse("2026-07-15T00:00:00Z"),
        )
        val evening = dose("evening", DoseStatus.PENDING).copy(
            key = "dose-evening",
            slot = MedicationSlot.EVENING,
            scheduledAt = Instant.parse("2026-07-15T09:00:00Z"),
        )
        val repository = PatientRepository(object : PatientDataSource {
            override suspend fun today() = listOf(morning, evening)
            override suspend fun slotTimes() = PatientSlotTimes.DEFAULT
            override suspend fun medications() = listOf(
                medication("morning", 10.0),
                medication("evening", 10.0),
            )
            override suspend fun recordDose(dose: PatientDose) = Unit
            override suspend fun recordPrn(medication: PatientMedication) = Unit
            override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
            override suspend fun revokeSession() = Unit
        })
        repository.handleNotificationTarget("2026-07-15", "evening")

        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                PatientHomeScreen(repository = repository, onUnlink = {}, tutorialEnabled = false)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.state.value.doses.size == 2 && repository.state.value.notificationTarget == null
        }
        composeRule.onNodeWithTag("patient-today-next-dose-dose-evening").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-today-next-dose-dose-morning").assertDoesNotExist()
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-notification-target-light.png")
    }

    @Test
    fun detailContentShowsCanonicalMedicationInformation() {
        val activity = showDoseDetail(notes = "夕食後に服用")

        composeRule.onAllNodesWithText("血圧の薬 5 mg").assertCountEquals(2)
        composeRule.onNodeWithText("2026/07/14 12:30").assertIsDisplayed()
        composeRule.onNodeWithText("記録済み").assertIsDisplayed()
        composeRule.onNodeWithText("メモ").assertIsDisplayed()
        composeRule.onNodeWithText("夕食後に服用").assertIsDisplayed()
        composeRule.onNodeWithText("1回に飲む量").assertIsDisplayed()
        composeRule.onNodeWithText("1回1.5錠").assertIsDisplayed()
        composeRule.onAllNodesWithText("薬の強さ").assertCountEquals(0)
        composeRule.onAllNodesWithText("現在の在庫").assertCountEquals(0)
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-102-patient-dose-detail-light.png")
    }

    @Test
    fun detailContentShowsCurrentIosEmptyNotesFallback() {
        showDoseDetail(notes = null)

        composeRule.onNodeWithText("メモはありません").assertIsDisplayed()
        composeRule.onNodeWithText("1回1.5錠").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-102-patient-dose-detail-empty-notes-light.png",
        )
    }

    @Test
    fun detailContentShowsCurrentIosLoadingOverlay() {
        setDoseDetail(notes = "夕食後に服用", loading = true)

        composeRule.onNodeWithTag("patient-dose-detail-loading").assertIsDisplayed()
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-102-patient-dose-detail-loading-light.png",
        )
    }

    @Test
    fun detailContentShowsRetryableCurrentIosError() {
        var retryCount = 0
        setDoseDetail(notes = null, error = true, onRetry = { retryCount += 1 })

        composeRule.onNodeWithText("取得に失敗しました").assertIsDisplayed()
        composeRule.onNodeWithText("再試行").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-102-patient-dose-detail-error-light.png",
        )
    }

    @Test
    fun detailContentPreservesHierarchyInDarkTheme() {
        setDoseDetail(notes = "夕食後に服用", darkTheme = true)

        composeRule.onAllNodesWithText("血圧の薬 5 mg").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("メモ").assertIsDisplayed()
        composeRule.onNodeWithText("夕食後に服用").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-102-patient-dose-detail-dark.png",
        )
    }

    @Test
    fun detailContentRemainsReachableAtTwoHundredPercentFontScale() {
        setDoseDetail(notes = "夕食後に服用", fontScale = 2f)

        composeRule.onNodeWithText("1回に飲む量").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1回1.5錠").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-102-patient-dose-detail-font-2.0.png",
        )
    }

    @Test
    fun reminderMaintenanceWarningDoesNotReplaceMutationSuccess() {
        val warning = "服薬記録は保存されましたが、通知予定を更新できませんでした。アプリを開いたときに再試行します。"
        composeRule.setContent {
            MedicationAppTheme {
                TodayContent(
                    doses = listOf(dose("med", DoseStatus.TAKEN)),
                    loading = false,
                    updatingKey = null,
                    error = null,
                    message = "服薬を記録しました。",
                    maintenanceWarning = warning,
                    medications = emptyMap(),
                    nextSlot = null,
                    updatingSlot = null,
                    prnMedications = emptyList(),
                    updatingPrnMedicationId = null,
                    onRetry = {},
                    onRecord = {},
                    onDetail = {},
                    onRecordSlot = {},
                    onRecordPrn = {},
                    onRemind = {},
                )
            }
        }

        composeRule.onNodeWithText("服薬を記録しました。").assertIsDisplayed()
        composeRule.onNodeWithText(warning).assertIsDisplayed()
    }

    private fun dose(id: String, status: DoseStatus) = PatientDose(
        key = "dose-$id", medicationId = id, scheduledAt = Instant.parse("2026-07-13T23:00:00Z"),
        status = status, medicationName = medicationName(id), dosageText = "1錠", doseCount = 1.0, slot = MedicationSlot.MORNING,
    )

    private fun medication(id: String, quantity: Double, isPrn: Boolean = false) = PatientMedication(
        id = id, patientId = "patient", name = medicationName(id), dosageText = "1錠", doseCountPerIntake = 1.0,
        dosageStrengthValue = 1.0, dosageStrengthUnit = "mg", notes = null, isPrn = isPrn,
        prnInstructions = if (isPrn) "痛い時" else null, startDate = Instant.EPOCH, endDate = null,
        inventoryCount = quantity, inventoryUnit = "錠", inventoryEnabled = true, inventoryQuantity = quantity,
        inventoryOut = quantity <= 0, isActive = true, isArchived = false, nextScheduledAt = null,
        regimenTimes = null, regimenDaysOfWeek = null,
    )

    private fun medicationName(id: String) = when (id) {
        "enough" -> "血圧の薬 5 mg"
        "short" -> "胃薬"
        "prn" -> "頭痛薬"
        else -> id
    }

    private fun showDoseDetail(notes: String?): Activity {
        return setDoseDetail(notes = notes)
    }

    private fun setDoseDetail(
        notes: String?,
        loading: Boolean = false,
        error: Boolean = false,
        onRetry: () -> Unit = {},
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ): Activity {
        lateinit var activity: Activity
        val medication = medication("med", 12.0).copy(
            notes = notes,
            dosageStrengthValue = 5.0,
            dosageStrengthUnit = "mg",
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
                        PatientDoseDetailContent(
                            dose("med", DoseStatus.TAKEN).copy(
                                medicationName = "血圧の薬 5 mg",
                                dosageText = "1回1錠",
                                doseCount = 1.5,
                                scheduledAt = Instant.parse("2026-07-14T03:30:00Z"),
                            ),
                            medication,
                            loading = loading,
                            error = error,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
        return activity
    }

    private fun showPrnSheet(
        prnError: String? = null,
        updatingPrnMedicationId: String? = null,
        successRevision: () -> Long = { 0L },
        onClearFeedback: () -> Unit = {},
        prnQuantity: Double = 10.0,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        onRecordPrn: (PatientMedication) -> Unit = {},
    ): Activity {
        lateinit var activity: Activity
        val prn = medication("prn", prnQuantity, isPrn = true)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    Box(Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding()) {
                        TodayContent(
                            doses = emptyList(),
                            loading = false,
                            updatingKey = null,
                            error = null,
                            message = null,
                            maintenanceWarning = null,
                            medications = mapOf(prn.id to prn),
                            nextSlot = null,
                            updatingSlot = null,
                            prnMedications = listOf(prn),
                            updatingPrnMedicationId = updatingPrnMedicationId,
                            onRetry = {},
                            onRecord = {},
                            onDetail = {},
                            onRecordSlot = {},
                            onRecordPrn = onRecordPrn,
                            onRemind = {},
                            prnError = prnError,
                            prnSuccessRevision = successRevision(),
                            onClearPrnFeedback = onClearFeedback,
                            now = Instant.parse("2026-07-14T03:00:00Z"),
                        )
                    }
                }
            }
        }
        return activity
    }

    private fun capturePrnSheet(filename: String) {
        if (Build.VERSION.SDK_INT >= 28) {
            writeScreenshotFixture(
                composeRule.onNodeWithTag("patient-prn-sheet").captureToImage(),
                filename,
            )
        } else {
            writeDeviceScreenshotFixture(filename)
        }
    }

    private fun showTodayState(
        loading: Boolean = false,
        refreshing: Boolean = false,
        error: String? = null,
        doses: List<PatientDose> = emptyList(),
    ) {
        val medications = doses.map { medication(it.medicationId, 10.0) }.associateBy(PatientMedication::id)
        composeRule.setContent {
            MedicationAppTheme {
                Box(Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding()) {
                    TodayContent(
                        doses = doses,
                        loading = loading,
                        refreshing = refreshing,
                        updatingKey = null,
                        error = error,
                        message = null,
                        maintenanceWarning = null,
                        medications = medications,
                        nextSlot = doses.firstOrNull()?.slot,
                        updatingSlot = null,
                        prnMedications = emptyList(),
                        updatingPrnMedicationId = null,
                        onRetry = {},
                        onRecord = {},
                        onDetail = {},
                        onRecordSlot = {},
                        onRecordPrn = {},
                        onRemind = {},
                        now = Instant.parse("2026-07-13T23:15:00Z"),
                    )
                }
            }
        }
    }

    private fun showProductionInitialTodayState(fail: Boolean): Pair<Activity, PatientRepository> {
        lateinit var activity: Activity
        val repository = PatientRepository(object : PatientDataSource {
            override suspend fun today(): List<PatientDose> = emptyList()
            override suspend fun slotTimes() = PatientSlotTimes.DEFAULT
            override suspend fun medications(): List<PatientMedication> {
                if (fail) error("synthetic C42 initial-load failure")
                delay(60_000)
                return emptyList()
            }
            override suspend fun recordDose(dose: PatientDose) = Unit
            override suspend fun recordPrn(medication: PatientMedication) = Unit
            override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
            override suspend fun revokeSession() = Unit
        })
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                PatientHomeScreen(repository = repository, onUnlink = {}, tutorialEnabled = false)
            }
        }
        return activity to repository
    }

    private fun showMatchedAdaptiveToday(
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ): Activity {
        lateinit var activity: Activity
        val scheduledAt = Instant.parse("2026-07-15T03:30:00Z")
        val now = Instant.parse("2026-07-15T03:00:00Z")
        val regularMedications = listOf(
            medication("enough", 10.0),
            medication("short", 10.0),
        )
        val prn = medication("prn", 10.0, isPrn = true)
        val doses = listOf(
            dose("enough", DoseStatus.PENDING).copy(scheduledAt = scheduledAt, slot = MedicationSlot.NOON),
            dose("short", DoseStatus.PENDING).copy(scheduledAt = scheduledAt, slot = MedicationSlot.NOON),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    Box(Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding()) {
                        TodayContent(
                            doses = doses,
                            loading = false,
                            updatingKey = null,
                            error = null,
                            message = null,
                            maintenanceWarning = null,
                            medications = (regularMedications + prn).associateBy(PatientMedication::id),
                            nextSlot = MedicationSlot.NOON,
                            updatingSlot = null,
                            prnMedications = listOf(prn),
                            updatingPrnMedicationId = null,
                            onRetry = {},
                            onRecord = {},
                            onDetail = {},
                            onRecordSlot = {},
                            onRecordPrn = {},
                            onRemind = {},
                            now = now,
                        )
                    }
                }
            }
        }
        return activity
    }

    @Suppress("DEPRECATION")
    private fun normalizeStatusBar(activity: Activity, darkTheme: Boolean = false) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = !darkTheme
    }
}
