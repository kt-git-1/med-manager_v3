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
            return NSLocalizedString("notification.slot.morning.body", comment: "Morning medication notification")
        case .noon:
            return NSLocalizedString("notification.slot.noon.body", comment: "Noon medication notification")
        case .evening:
            return NSLocalizedString("notification.slot.evening.body", comment: "Evening medication notification")
        case .bedtime:
            return NSLocalizedString("notification.slot.bedtime.body", comment: "Bedtime medication notification")
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

    init(timeZone: TimeZone = AppConstants.defaultTimeZone) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        self.calendar = calendar

        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = timeZone
        formatter.locale = AppConstants.posixLocale
        formatter.dateFormat = "yyyy-MM-dd"
        self.dateFormatter = formatter
    }

    func buildPlan(
        monthSummaries: [HistoryMonthResponseDTO],
        includeSecondary: Bool,
        enabledSlots: Set<NotificationSlot> = Set(NotificationSlot.allCases),
        slotTimes: [NotificationSlot: (hour: Int, minute: Int)] = [:],
        now: Date = Date()
    ) -> [NotificationPlanEntry] {
        let summariesByDate = Dictionary(
            uniqueKeysWithValues: monthSummaries.flatMap { month in
                month.days.map { ($0.date, $0.slotSummary) }
            }
        )

        let start = calendar.startOfDay(for: now)
        let dates = (0..<AppConstants.notificationLookaheadDays).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }

        var entries: [NotificationPlanEntry] = []
        for date in dates {
            let dateKey = dateFormatter.string(from: date)
            guard let summary = summariesByDate[dateKey] else { continue }
            for slot in NotificationSlot.allCases
                where enabledSlots.contains(slot) && slot.status(from: summary) == .pending {
                guard let scheduledAt = scheduledDate(for: slot, on: date, slotTimes: slotTimes) else {
                    continue
                }
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
                            scheduledAt: scheduledAt.addingTimeInterval(AppConstants.secondaryReminderDelay)
                        )
                    )
                }
            }
        }

        return entries.sorted { $0.scheduledAt < $1.scheduledAt }
    }

    private func scheduledDate(
        for slot: NotificationSlot,
        on date: Date,
        slotTimes: [NotificationSlot: (hour: Int, minute: Int)]
    ) -> Date? {
        var components = calendar.dateComponents([.year, .month, .day], from: date)
        let override = slotTimes[slot] ?? slot.hourMinute
        components.hour = override.hour
        components.minute = override.minute
        components.second = 0
        return calendar.date(from: components)
    }
}
