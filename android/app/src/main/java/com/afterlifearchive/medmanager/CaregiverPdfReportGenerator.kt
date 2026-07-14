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

data class GeneratedPdfReport(val file: File, val contentUri: android.net.Uri)

object CaregiverPdfReportGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val BOTTOM = 800f

    fun generate(context: Context, report: CaregiverHistoryReport): GeneratedPdfReport {
        val directory = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        val file = File(directory, "medication_report_${report.range.from}_${report.range.to}.pdf")
        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 80, 77); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 45, 44); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 53, 52); textSize = 10.5f }
        val mutedPaint = Paint(bodyPaint).apply { color = Color.rgb(95, 105, 104); textSize = 9f }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN

        fun finishPage() {
            val current = page ?: return
            current.canvas.drawText(pageNumber.toString(), PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, mutedPaint)
            document.finishPage(current)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            page!!.canvas.drawColor(Color.WHITE)
            y = MARGIN
            if (pageNumber > 1) {
                page!!.canvas.drawText("服薬レポート（続き）", MARGIN, y, mutedPaint)
                y += 24f
            }
        }

        fun line(text: String, paint: Paint = bodyPaint, indent: Float = 0f, spaceAfter: Float = 16f) {
            if (y + spaceAfter > BOTTOM) newPage()
            page!!.canvas.drawText(text.take(88), MARGIN + indent, y, paint)
            y += spaceAfter
        }

        try {
            newPage()
            line("お薬の見守り 服薬レポート", titlePaint, spaceAfter = 34f)
            line("対象: ${report.patient.displayName}さん", headingPaint, spaceAfter = 22f)
            line("期間: ${report.range.from} 〜 ${report.range.to}（${report.range.days}日間）", bodyPaint, spaceAfter = 20f)
            line("服薬達成率: ${adherenceRate(report)}", headingPaint, spaceAfter = 30f)
            line("このレポートは端末内で作成されました。", mutedPaint, spaceAfter = 26f)

            report.days.forEach { day ->
                if (y > BOTTOM - 90) newPage()
                line(day.date, headingPaint, spaceAfter = 21f)
                val slots = listOf(
                    "朝" to day.slots.morning,
                    "昼" to day.slots.noon,
                    "夕" to day.slots.evening,
                    "就寝前" to day.slots.bedtime,
                )
                slots.forEach { (slot, items) ->
                    items.forEach { item ->
                        line("$slot  ${item.name}  ${item.dosageText}  ${statusLabel(item.status)}", bodyPaint, indent = 8f, spaceAfter = 16f)
                        item.recordedBy?.let { line("記録者: ${actorLabel(it)}", mutedPaint, indent = 24f, spaceAfter = 14f) }
                    }
                }
                day.prn.forEach { item ->
                    line("頓服  ${item.name}  ${item.dosageText}  ${formatNumber(item.quantity)}錠", bodyPaint, indent = 8f, spaceAfter = 16f)
                    line("記録者: ${actorLabel(item.recordedBy)}", mutedPaint, indent = 24f, spaceAfter = 14f)
                }
                if (day.slots.all().isEmpty() && day.prn.isEmpty()) line("記録なし", mutedPaint, indent = 8f, spaceAfter = 18f)
                y += 7f
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

    internal fun adherenceRate(report: CaregiverHistoryReport): String {
        val scheduled = report.days.flatMap { it.slots.all() }
        if (scheduled.isEmpty()) return "—"
        val taken = scheduled.count { it.status.equals("TAKEN", true) }
        return "${taken * 100 / scheduled.size}%"
    }

    private fun statusLabel(value: String) = when (value.uppercase()) { "TAKEN" -> "服用済み"; "MISSED" -> "未服用"; else -> "未記録" }
    private fun actorLabel(value: String) = if (value.equals("PATIENT", true)) "本人" else "見守る人"
    private fun formatNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
}
