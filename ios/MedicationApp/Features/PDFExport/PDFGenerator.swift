import UIKit

// ---------------------------------------------------------------------------
// 011-pdf-export: On-device PDF generation using UIGraphicsPDFRenderer
// ---------------------------------------------------------------------------

enum PDFGenerator {

    // MARK: - Page Metrics (A4)

    private static let pageWidth: CGFloat = 595
    private static let pageHeight: CGFloat = 842
    private static let pageRect = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)
    private static let margin: CGFloat = 40
    private static let contentWidth = pageWidth - 2 * margin
    private static let lineSpacing: CGFloat = 4

    // MARK: - Fonts

    private static let titleFont = UIFont.boldSystemFont(ofSize: 20)
    private static let headingFont = UIFont.boldSystemFont(ofSize: 14)
    private static let bodyFont = UIFont.systemFont(ofSize: 11)
    private static let bodyBoldFont = UIFont.boldSystemFont(ofSize: 11)
    private static let smallFont = UIFont.systemFont(ofSize: 9)

    // MARK: - Adherence Calculation

    static func adherenceRate(from report: HistoryReportResponseDTO) -> String {
        var taken = 0
        var missed = 0
        for day in report.days {
            let allItems = day.slots.morning + day.slots.noon + day.slots.evening + day.slots.bedtime
            for item in allItems {
                switch item.status {
                case "TAKEN":  taken += 1
                case "MISSED": missed += 1
                default: break
                }
            }
        }
        let denominator = taken + missed
        guard denominator > 0 else { return "—" }
        let rate = Double(taken) / Double(denominator) * 100
        return String(format: "%.0f%%", rate)
    }

    // MARK: - Generate PDF

    static func generate(from report: HistoryReportResponseDTO) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)

        let data = renderer.pdfData { context in
            // ---- Page 1: Summary ----
            context.beginPage()
            _ = drawSummaryPage(report: report, context: context)

            // ---- Page 2+: Daily Detail ----
            context.beginPage()
            var y = margin

            for (index, day) in report.days.enumerated() {
                // Check if we need a new page for the date header
                if y + 30 > pageHeight - margin {
                    context.beginPage()
                    y = margin
                }

                // Date header
                y = drawText(
                    day.date,
                    font: headingFont,
                    at: CGPoint(x: margin, y: y),
                    maxWidth: contentWidth
                )
                y += 4

                // Separator line
                let ctx = context.cgContext
                ctx.setStrokeColor(UIColor.separator.cgColor)
                ctx.setLineWidth(0.5)
                ctx.move(to: CGPoint(x: margin, y: y))
                ctx.addLine(to: CGPoint(x: pageWidth - margin, y: y))
                ctx.strokePath()
                y += 6

                let allSlotItems = day.slots.morning + day.slots.noon
                    + day.slots.evening + day.slots.bedtime
                let hasPrn = !day.prn.isEmpty

                if allSlotItems.isEmpty && !hasPrn {
                    y = drawText(
                        NSLocalizedString("pdfexport.pdf.noRecords", comment: "No records"),
                        font: bodyFont,
                        color: .secondaryLabel,
                        at: CGPoint(x: margin + 8, y: y),
                        maxWidth: contentWidth - 8
                    )
                    y += 8
                } else {
                    // Scheduled slots
                    let slotPairs: [(String, [HistoryReportSlotItemDTO])] = [
                        (NSLocalizedString("pdfexport.pdf.slot.morning", comment: "Morning"), day.slots.morning),
                        (NSLocalizedString("pdfexport.pdf.slot.noon", comment: "Noon"), day.slots.noon),
                        (NSLocalizedString("pdfexport.pdf.slot.evening", comment: "Evening"), day.slots.evening),
                        (NSLocalizedString("pdfexport.pdf.slot.bedtime", comment: "Bedtime"), day.slots.bedtime),
                    ]

                    for (slotLabel, items) in slotPairs {
                        guard !items.isEmpty else { continue }

                        if y + 20 > pageHeight - margin {
                            context.beginPage()
                            y = margin
                        }

                        y = drawText(
                            "【\(slotLabel)】",
                            font: bodyBoldFont,
                            at: CGPoint(x: margin + 8, y: y),
                            maxWidth: contentWidth - 8
                        )
                        y += 2

                        for item in items {
                            if y + 16 > pageHeight - margin {
                                context.beginPage()
                                y = margin
                            }

                            let statusLabel = statusText(for: item.status)
                            let doseCountStr = AppConstants.formatDecimal(item.doseCount)
                            let line = "  \(item.name) \(item.dosageText) ×\(doseCountStr)　\(statusLabel)"
                            y = drawText(
                                line,
                                font: bodyFont,
                                at: CGPoint(x: margin + 16, y: y),
                                maxWidth: contentWidth - 24
                            )

                            if let recordedAt = item.recordedAt, item.status == "TAKEN" {
                                let timeStr = formatRecordedTime(recordedAt)
                                y = drawText(
                                    "    記録: \(timeStr)",
                                    font: smallFont,
                                    color: .secondaryLabel,
                                    at: CGPoint(x: margin + 24, y: y),
                                    maxWidth: contentWidth - 32
                                )
                            }
                            y += 2
                        }
                        y += 4
                    }

                    // PRN section
                    if hasPrn {
                        if y + 20 > pageHeight - margin {
                            context.beginPage()
                            y = margin
                        }

                        y = drawText(
                            "【\(NSLocalizedString("pdfexport.pdf.prn", comment: "PRN"))】",
                            font: bodyBoldFont,
                            at: CGPoint(x: margin + 8, y: y),
                            maxWidth: contentWidth - 8
                        )
                        y += 2

                        for prnItem in day.prn {
                            if y + 16 > pageHeight - margin {
                                context.beginPage()
                                y = margin
                            }

                            let qtyStr = AppConstants.formatDecimal(prnItem.quantity)
                            let recorderLabel = recorderText(for: prnItem.recordedBy)
                            let timeStr = formatRecordedTime(prnItem.recordedAt)
                            let line = "  \(prnItem.name) \(prnItem.dosageText) ×\(qtyStr)　\(timeStr)　\(recorderLabel)"
                            y = drawText(
                                line,
                                font: bodyFont,
                                at: CGPoint(x: margin + 16, y: y),
                                maxWidth: contentWidth - 24
                            )
                            y += 2
                        }
                        y += 4
                    }
                }

                // Spacing between days
                if index < report.days.count - 1 {
                    y += 8
                }
            }
        }

        // Write to temp file
        let tempDir = FileManager.default.temporaryDirectory
        let fileName = "medication_report_\(report.range.from)_\(report.range.to).pdf"
        let fileURL = tempDir.appendingPathComponent(fileName)
        try data.write(to: fileURL)
        return fileURL
    }

    // MARK: - Summary Page

    @discardableResult
    private static func drawSummaryPage(
        report: HistoryReportResponseDTO,
        context: UIGraphicsPDFRendererContext
    ) -> CGFloat {
        var y = margin

        // Title
        y = drawText(
            NSLocalizedString("pdfexport.pdf.title", comment: "Report title"),
            font: titleFont,
            at: CGPoint(x: margin, y: y),
            maxWidth: contentWidth
        )
        y += 12

        // Patient name
        let patientStr = String(
            format: NSLocalizedString("pdfexport.pdf.patient", comment: "Patient"),
            report.patient.displayName
        )
        y = drawText(patientStr, font: bodyFont, at: CGPoint(x: margin, y: y), maxWidth: contentWidth)
        y += 4

        // Period
        let periodStr = String(
            format: NSLocalizedString("pdfexport.pdf.period", comment: "Period"),
            report.range.from, report.range.to
        )
        y = drawText(periodStr, font: bodyFont, at: CGPoint(x: margin, y: y), maxWidth: contentWidth)
        y += 4

        // Generated timestamp
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy/MM/dd HH:mm"
        dateFormatter.timeZone = TimeZone(identifier: "Asia/Tokyo")
        let timestampStr = String(
            format: NSLocalizedString("pdfexport.pdf.generated", comment: "Generated"),
            dateFormatter.string(from: Date())
        )
        y = drawText(
            timestampStr, font: smallFont, color: .secondaryLabel,
            at: CGPoint(x: margin, y: y), maxWidth: contentWidth
        )
        y += 20

        // Summary section header
        y = drawText(
            NSLocalizedString("pdfexport.pdf.summary", comment: "Summary"),
            font: headingFont,
            at: CGPoint(x: margin, y: y),
            maxWidth: contentWidth
        )
        y += 8

        // Count statuses
        var taken = 0
        var missed = 0
        var pending = 0
        var prnCount = 0
        for day in report.days {
            let allItems = day.slots.morning + day.slots.noon
                + day.slots.evening + day.slots.bedtime
            for item in allItems {
                switch item.status {
                case "TAKEN":   taken += 1
                case "MISSED":  missed += 1
                case "PENDING": pending += 1
                default: break
                }
            }
            prnCount += day.prn.count
        }

        // Scheduled summary
        let scheduledLabel = NSLocalizedString("pdfexport.pdf.scheduled", comment: "Scheduled")
        let takenLabel = NSLocalizedString("pdfexport.pdf.status.taken", comment: "Taken")
        let missedLabel = NSLocalizedString("pdfexport.pdf.status.missed", comment: "Missed")
        let pendingLabel = NSLocalizedString("pdfexport.pdf.status.pending", comment: "Pending")
        y = drawText(
            "\(scheduledLabel): \(takenLabel) \(taken) / \(missedLabel) \(missed) / \(pendingLabel) \(pending)",
            font: bodyFont,
            at: CGPoint(x: margin + 8, y: y),
            maxWidth: contentWidth - 8
        )
        y += 4

        // PRN summary
        let prnLabel = NSLocalizedString("pdfexport.pdf.prn", comment: "PRN")
        y = drawText(
            "\(prnLabel): \(prnCount)件",
            font: bodyFont,
            at: CGPoint(x: margin + 8, y: y),
            maxWidth: contentWidth - 8
        )
        y += 8

        // Adherence rate
        let adherenceLabel = NSLocalizedString("pdfexport.pdf.adherence", comment: "Adherence")
        let rateStr = adherenceRate(from: report)
        y = drawText(
            "\(adherenceLabel): \(rateStr)",
            font: bodyBoldFont,
            at: CGPoint(x: margin + 8, y: y),
            maxWidth: contentWidth - 8
        )

        return y
    }

    // MARK: - Drawing Helpers

    @discardableResult
    private static func drawText(
        _ text: String,
        font: UIFont,
        color: UIColor = .label,
        at point: CGPoint,
        maxWidth: CGFloat
    ) -> CGFloat {
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.lineBreakMode = .byWordWrapping

        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: color,
            .paragraphStyle: paragraphStyle,
        ]

        let attributedString = NSAttributedString(string: text, attributes: attributes)
        let boundingRect = attributedString.boundingRect(
            with: CGSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            context: nil
        )

        let drawRect = CGRect(
            x: point.x,
            y: point.y,
            width: maxWidth,
            height: boundingRect.height
        )
        attributedString.draw(in: drawRect)

        return point.y + ceil(boundingRect.height) + lineSpacing
    }

    private static func statusText(for status: String) -> String {
        switch status {
        case "TAKEN":   return NSLocalizedString("pdfexport.pdf.status.taken", comment: "Taken")
        case "MISSED":  return NSLocalizedString("pdfexport.pdf.status.missed", comment: "Missed")
        case "PENDING": return NSLocalizedString("pdfexport.pdf.status.pending", comment: "Pending")
        default:        return status
        }
    }

    private static func recorderText(for recorder: String) -> String {
        switch recorder {
        case "PATIENT":   return NSLocalizedString("pdfexport.pdf.recorder.patient", comment: "Patient")
        case "CAREGIVER": return NSLocalizedString("pdfexport.pdf.recorder.caregiver", comment: "Caregiver")
        default:          return recorder
        }
    }

    private static func formatRecordedTime(_ isoString: String) -> String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = isoFormatter.date(from: isoString) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateFormat = "HH:mm"
            displayFormatter.timeZone = TimeZone(identifier: "Asia/Tokyo")
            return displayFormatter.string(from: date)
        }
        // Retry without fractional seconds
        isoFormatter.formatOptions = [.withInternetDateTime]
        if let date = isoFormatter.date(from: isoString) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateFormat = "HH:mm"
            displayFormatter.timeZone = TimeZone(identifier: "Asia/Tokyo")
            return displayFormatter.string(from: date)
        }
        return isoString
    }
}
