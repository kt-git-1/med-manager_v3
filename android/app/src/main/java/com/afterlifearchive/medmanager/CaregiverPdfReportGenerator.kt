package com.afterlifearchive.medmanager

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryReport
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class GeneratedPdfReport(val file: File, val contentUri: android.net.Uri)

internal data class CaregiverPdfSummary(
    val taken: Int,
    val missed: Int,
    val pending: Int,
    val prn: Int,
)

object CaregiverPdfReportGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
    private const val PAGE_BOTTOM = PAGE_HEIGHT - MARGIN
    private val TOKYO = ZoneId.of("Asia/Tokyo")
    private val generatedFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.JAPANESE).withZone(TOKYO)
    private val recordedFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPANESE).withZone(TOKYO)

    fun generate(
        context: Context,
        report: CaregiverHistoryReport,
        generatedAt: Instant = Instant.now(),
    ): GeneratedPdfReport {
        val directory = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        val file = File(directory, "medication_report_${report.range.from}_${report.range.to}.pdf")
        val document = PdfDocument()
        val titlePaint = paint(20f, bold = true)
        val headingPaint = paint(14f, bold = true)
        val bodyPaint = paint(11f)
        val bodyBoldPaint = paint(11f, bold = true)
        val smallPaint = paint(9f, color = Color.rgb(110, 110, 115))
        val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 210, 215)
            strokeWidth = 0.5f
        }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN

        fun finishPage() {
            page?.let(document::finishPage)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            page!!.canvas.drawColor(Color.WHITE)
            y = MARGIN
        }

        fun draw(text: String, textPaint: Paint, x: Float = MARGIN, maxWidth: Float = CONTENT_WIDTH): Float {
            val lines = wrap(text, textPaint, maxWidth)
            val lineHeight = textPaint.fontSpacing + 4f
            lines.forEach { line ->
                if (y + lineHeight > PAGE_BOTTOM) newPage()
                page!!.canvas.drawText(line, x, y - textPaint.fontMetrics.ascent, textPaint)
                y += lineHeight
            }
            return y
        }

        try {
            // Current iOS contract: page 1 is always a standalone summary page.
            newPage()
            draw("服用履歴レポート", titlePaint)
            y += 12f
            draw("対象者：${report.patient.displayName}", bodyPaint)
            y += 4f
            draw("期間：${report.range.from}〜${report.range.to}", bodyPaint)
            y += 4f
            draw("作成日時：${generatedFormatter.format(generatedAt)}", smallPaint)
            y += 20f
            draw("集計", headingPaint)
            y += 8f
            val summary = summary(report)
            draw("定時: 服用済 ${summary.taken} / 未服用 ${summary.missed} / 未記録 ${summary.pending}", bodyPaint, MARGIN + 8f, CONTENT_WIDTH - 8f)
            y += 4f
            draw("頓服: ${summary.prn}件", bodyPaint, MARGIN + 8f, CONTENT_WIDTH - 8f)
            y += 8f
            draw("服用率: ${adherenceRate(report)}", bodyBoldPaint, MARGIN + 8f, CONTENT_WIDTH - 8f)

            // Current iOS contract: daily detail begins on page 2 even for a short report.
            newPage()
            report.days.forEachIndexed { index, day ->
                if (y + 30f > PAGE_BOTTOM) newPage()
                draw(day.date, headingPaint)
                y += 4f
                page!!.canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
                y += 6f
                val slots = listOf(
                    "朝" to day.slots.morning,
                    "昼" to day.slots.noon,
                    "夜" to day.slots.evening,
                    "眠前" to day.slots.bedtime,
                )
                if (day.slots.all().isEmpty() && day.prn.isEmpty()) {
                    draw("記録なし", smallPaint, MARGIN + 8f, CONTENT_WIDTH - 8f)
                    y += 8f
                } else {
                    slots.filter { it.second.isNotEmpty() }.forEach { (slot, items) ->
                        if (y + 20f > PAGE_BOTTOM) newPage()
                        draw("【$slot】", bodyBoldPaint, MARGIN + 8f, CONTENT_WIDTH - 8f)
                        y += 2f
                        items.forEach { item ->
                            if (y + 16f > PAGE_BOTTOM) newPage()
                            draw(
                                "  ${item.name} ${item.dosageText} ×${formatNumber(item.doseCount)}　${statusLabel(item.status)}",
                                bodyPaint,
                                MARGIN + 16f,
                                CONTENT_WIDTH - 24f,
                            )
                            if (item.status.equals("TAKEN", true) && item.recordedAt != null) {
                                val details = listOfNotNull(formatRecordedTime(item.recordedAt), item.recordedBy?.let(::recorderLabel))
                                draw("    記録: ${details.joinToString(" / ")}", smallPaint, MARGIN + 24f, CONTENT_WIDTH - 32f)
                            }
                            y += 2f
                        }
                        y += 4f
                    }
                    if (day.prn.isNotEmpty()) {
                        if (y + 20f > PAGE_BOTTOM) newPage()
                        draw("【頓服】", bodyBoldPaint, MARGIN + 8f, CONTENT_WIDTH - 8f)
                        y += 2f
                        day.prn.forEach { item ->
                            if (y + 16f > PAGE_BOTTOM) newPage()
                            draw(
                                "  ${item.name} ${item.dosageText} ×${formatNumber(item.quantity)}　${formatRecordedTime(item.recordedAt)}　${recorderLabel(item.recordedBy)}",
                                bodyPaint,
                                MARGIN + 16f,
                                CONTENT_WIDTH - 24f,
                            )
                            y += 2f
                        }
                        y += 4f
                    }
                }
                if (index < report.days.lastIndex) y += 8f
            }
            finishPage()
            FileOutputStream(file).use(document::writeTo)
        } finally {
            finishPage()
            document.close()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return GeneratedPdfReport(file, uri)
    }

    internal fun summary(report: CaregiverHistoryReport): CaregiverPdfSummary {
        val scheduled = report.days.flatMap { it.slots.all() }
        return CaregiverPdfSummary(
            taken = scheduled.count { it.status.equals("TAKEN", true) },
            missed = scheduled.count { it.status.equals("MISSED", true) },
            pending = scheduled.count { it.status.equals("PENDING", true) },
            prn = report.days.sumOf { it.prn.size },
        )
    }

    internal fun adherenceRate(report: CaregiverHistoryReport): String {
        val summary = summary(report)
        val denominator = summary.taken + summary.missed
        if (denominator == 0) return "—"
        return "${Math.round(summary.taken.toDouble() / denominator * 100)}%"
    }

    internal fun statusLabel(value: String) = when (value.uppercase(Locale.US)) {
        "TAKEN" -> "服用済"
        "MISSED" -> "未服用"
        "PENDING" -> "未記録"
        else -> value
    }

    internal fun recorderLabel(value: String) = when (value.uppercase(Locale.US)) {
        "PATIENT" -> "本人が記録"
        "CAREGIVER" -> "家族が代理で記録"
        else -> value
    }

    internal fun formatRecordedTime(value: String): String = runCatching {
        recordedFormatter.format(Instant.parse(value))
    }.getOrDefault(value)

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.rgb(32, 32, 36)) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null).coerceAtLeast(1)
            lines += text.substring(start, (start + count).coerceAtMost(text.length))
            start += count
        }
        return lines
    }

    private fun formatNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)
}
