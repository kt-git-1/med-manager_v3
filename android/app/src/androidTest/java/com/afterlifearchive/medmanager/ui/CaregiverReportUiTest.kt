package com.afterlifearchive.medmanager.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
        composeRule.onNodeWithText("プレミアムでPDF出力").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-lock-close").performClick()
    }

    @Test
    fun initialReleaseBillingGateRendersNoPdfEntry() {
        composeRule.setContent { MedicationAppTheme { CaregiverReportAction(repository(true), "p1", billingEnabled = false) } }
        composeRule.onAllNodesWithTag("caregiver-pdf-action").assertCountEquals(0)
        composeRule.onAllNodesWithTag("caregiver-pdf-picker").assertCountEquals(0)
    }

    @Test
    fun premiumPickerRejectsFutureCustomRange() {
        setContent(repository(premium = true))
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-preset-picker").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-preset-custom").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-to").performTextReplacement(LocalDate.now(TOKYO).plusDays(1).toString())
        composeRule.onNodeWithTag("caregiver-pdf-validation").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("終了日は今日以前を指定してください").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-generate").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun devicePdfFailureRemainsInPickerAndIsRetryable() {
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverReportAction(
                    repository(true),
                    "p1",
                    billingEnabled = true,
                    generatePdf = { _, _ -> error("disk full") },
                )
            }
        }
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-generate").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("caregiver-pdf-generation-failed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("caregiver-pdf-generation-failed").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-generate").assertIsDisplayed()
    }

    @Test
    fun generatingOverlayCoversFetchAndOnDeviceRendering() {
        val gate = CompletableDeferred<Unit>()
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverReportAction(
                    repository(true),
                    "p1",
                    billingEnabled = true,
                    generatePdf = { _, _ -> gate.await(); error("stop after assertion") },
                )
            }
        }
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-generate").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("caregiver-pdf-generating").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("caregiver-pdf-generating").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-pdf-generate").assertIsNotEnabled()
        gate.complete(Unit)
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
        ParcelFileDescriptor.open(generated.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(2, renderer.pageCount) }
        }
    }

    @Test
    fun generatorMatchesCurrentIosSummaryAndLabels() {
        val report = report()
        assertEquals(com.afterlifearchive.medmanager.CaregiverPdfSummary(1, 0, 0, 1), CaregiverPdfReportGenerator.summary(report))
        assertEquals("100%", CaregiverPdfReportGenerator.adherenceRate(report))
        assertEquals("服用済", CaregiverPdfReportGenerator.statusLabel("TAKEN"))
        assertEquals("未服用", CaregiverPdfReportGenerator.statusLabel("MISSED"))
        assertEquals("未記録", CaregiverPdfReportGenerator.statusLabel("PENDING"))
        assertEquals("本人が記録", CaregiverPdfReportGenerator.recorderLabel("PATIENT"))
        assertEquals("家族が代理で記録", CaregiverPdfReportGenerator.recorderLabel("CAREGIVER"))
        assertEquals("08:00", CaregiverPdfReportGenerator.formatRecordedTime("2026-07-14T23:00:00Z"))
    }

    @Test
    fun screenshotFixtureCoversFreeLock() {
        setContent(repository(premium = false))
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithText("プレミアムでPDF出力").assertIsDisplayed()
        writeDeviceScreenshotFixture("android-ui-207-pdf-lock-light-matched.png")
    }

    @Test
    fun screenshotFixturesCoverPremiumPickerAndRenderedPdf() {
        setContent(repository(premium = true))
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-picker").assertIsDisplayed()
        writeDeviceScreenshotFixture("android-ui-207-pdf-picker-light-matched.png")

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val generated = CaregiverPdfReportGenerator.generate(context, report(), Instant.parse("2026-07-15T03:34:00Z"))
        ParcelFileDescriptor.open(generated.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    writeScreenshotFixture(bitmap.asImageBitmap(), "android-ui-207-pdf-render-summary-matched.png")
                }
            }
        }
    }

    @Test
    fun pickerRemainsReachableAtTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(LocalDensity.current.density, 2f)) {
                MedicationAppTheme(darkTheme = true) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        CaregiverReportAction(repository(true), "p1", billingEnabled = true)
                    }
                }
            }
        }
        composeRule.onNodeWithTag("caregiver-pdf-action").performClick()
        composeRule.onNodeWithTag("caregiver-pdf-generate").performScrollTo().assertIsDisplayed()
        writeDeviceScreenshotFixture("android-ui-207-pdf-picker-dark-font-2.0-matched.png")
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
