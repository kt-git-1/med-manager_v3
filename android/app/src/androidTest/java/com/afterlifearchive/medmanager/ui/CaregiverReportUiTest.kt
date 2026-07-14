package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.afterlifearchive.medmanager.CaregiverPdfReportGenerator
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryReport
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportDay
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportPrnItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRange
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportSlotItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportSlots
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaregiverReportUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freeEntitlementShowsPdfLock() {
        setContent(repository(premium = false))
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithText("PDF出力はプレミアム機能です").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-lock-close").performClick()
    }

    @Test
    fun premiumPickerRejectsFutureCustomRange() {
        setContent(repository(premium = true))
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-preset-custom").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-to").performTextReplacement(LocalDate.now(TOKYO).plusDays(1).toString())
        composeRule.onNodeWithText("終了日は今日以前にしてください。").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-generate").assertIsNotEnabled()
    }

    @Test
    fun generatorCreatesPrivatePdfAndContentUri() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val generated = CaregiverPdfReportGenerator.generate(context, report())

        assertTrue(generated.file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
        val header = ByteArray(4)
        generated.file.inputStream().use { assertEquals(4, it.read(header)) }
        assertEquals("%PDF", String(header, Charsets.US_ASCII))
        assertEquals("content", generated.contentUri.scheme)
        assertEquals("${context.packageName}.fileprovider", generated.contentUri.authority)
    }

    private fun setContent(repository: CaregiverReportRepository) {
        composeRule.setContent { MedicationAppTheme { CaregiverReportAction(repository, "p1", billingEnabled = true) } }
        composeRule.waitForIdle()
    }

    private fun repository(premium: Boolean) = CaregiverReportRepository(object : CaregiverReportDataSource {
        override suspend fun premium() = premium
        override suspend fun report(patientId: String, from: LocalDate, to: LocalDate) = report()
    })

    private fun report() = CaregiverHistoryReport(
        CaregiverReportPatient("p1", "さくら"),
        CaregiverReportRange("2026-07-01", "2026-07-15", "Asia/Tokyo", 15),
        listOf(CaregiverReportDay(
            "2026-07-15",
            CaregiverReportSlots(
                listOf(CaregiverReportSlotItem("m1", "薬A", "1錠", 1.0, "TAKEN", "2026-07-14T23:00:00Z", "PATIENT")),
                emptyList(), emptyList(), emptyList(),
            ),
            listOf(CaregiverReportPrnItem("m2", "頓服", "1錠", 1.0, "2026-07-15T03:00:00Z", "CAREGIVER")),
        )),
    )

    companion object { private val TOKYO = java.time.ZoneId.of("Asia/Tokyo") }
}
