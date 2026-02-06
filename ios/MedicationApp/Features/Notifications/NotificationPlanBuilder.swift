import Foundation

struct NotificationPlanEntry: Equatable, Hashable {
    let dateKey: String
    let slot: NotificationSlot
    let sequence: Int
    let scheduledAt: Date

    var identifier: String {
        "notif:\(dateKey):\(slot.rawValue):\(sequence)"
    }

    var body: String {
        slot.notificationBody
    }
}

enum NotificationSlot: String, CaseIterable {
    case morning
    case noon
    case evening
    case bedtime

    var notificationBody: String {
        switch self {
        case .morning:
            return "朝のお薬の時間です！"
        case .noon:
            return "昼のお薬の時間です！"
        case .evening:
            return "夜のお薬の時間です！"
        case .bedtime:
            return "眠前のお薬の時間です！"
        }
    }

    var hourMinute: (hour: Int, minute: Int) {
        switch self {
        case .morning:
            return (8, 0)
        case .noon:
            return (12, 0)
        case .evening:
            return (19, 0)
        case .bedtime:
            return (22, 0)
        }
    }

    func status(from summary: HistorySlotSummaryDTO) -> HistorySlotSummaryStatusDTO {
        switch self {
        case .morning:
            return summary.morning
        case .noon:
            return summary.noon
        case .evening:
            return summary.evening
        case .bedtime:
            return summary.bedtime
        }
    }
}

struct NotificationPlanBuilder {
    private let calendar: Calendar
    private let dateFormatter: DateFormatter

    init(timeZone: TimeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        self.calendar = calendar

        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        self.dateFormatter = formatter
    }

    func buildPlan(
        monthSummaries: [HistoryMonthResponseDTO],
        includeSecondary: Bool,
        enabledSlots: Set<NotificationSlot> = Set(NotificationSlot.allCases),
        now: Date = Date()
    ) -> [NotificationPlanEntry] {
        let summariesByDate = Dictionary(
            uniqueKeysWithValues: monthSummaries.flatMap { month in
                month.days.map { ($0.date, $0.slotSummary) }
            }
        )

        let start = calendar.startOfDay(for: now)
        let dates = (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }

        var entries: [NotificationPlanEntry] = []
        for date in dates {
            let dateKey = dateFormatter.string(from: date)
            guard let summary = summariesByDate[dateKey] else { continue }
            for slot in NotificationSlot.allCases
                where enabledSlots.contains(slot) && slot.status(from: summary) == .pending {
                guard let scheduledAt = scheduledDate(for: slot, on: date) else { continue }
                entries.append(
                    NotificationPlanEntry(
                        dateKey: dateKey,
                        slot: slot,
                        sequence: 1,
                        scheduledAt: scheduledAt
                    )
                )
                if includeSecondary {
                    entries.append(
                        NotificationPlanEntry(
                            dateKey: dateKey,
                            slot: slot,
                            sequence: 2,
                            scheduledAt: scheduledAt.addingTimeInterval(15 * 60)
                        )
                    )
                }
            }
        }

        return entries.sorted { $0.scheduledAt < $1.scheduledAt }
    }

    private func scheduledDate(for slot: NotificationSlot, on date: Date) -> Date? {
        var components = calendar.dateComponents([.year, .month, .day], from: date)
        components.hour = slot.hourMinute.hour
        components.minute = slot.hourMinute.minute
        components.second = 0
        return calendar.date(from: components)
    }
}
