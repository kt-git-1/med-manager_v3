import Foundation

enum MedicationRecordingPolicy {
    static let opensBeforeScheduledSeconds: TimeInterval = 30 * 60
    static let lateThresholdSeconds: TimeInterval = 60 * 60

    static func deadline(for scheduledAt: Date, calendar: Calendar) -> Date {
        let startOfScheduledDay = calendar.startOfDay(for: scheduledAt)
        let nextDay = calendar.date(byAdding: .day, value: 1, to: startOfScheduledDay) ?? startOfScheduledDay
        return calendar.date(byAdding: .hour, value: 4, to: nextDay) ?? nextDay
    }

    static func isRecordable(scheduledAt: Date, now: Date, calendar: Calendar) -> Bool {
        let opensAt = scheduledAt.addingTimeInterval(-opensBeforeScheduledSeconds)
        return now >= opensAt && now < deadline(for: scheduledAt, calendar: calendar)
    }

    static func isLate(scheduledAt: Date, takenAt: Date) -> Bool {
        takenAt.timeIntervalSince(scheduledAt) >= lateThresholdSeconds
    }

    static func delayText(for seconds: TimeInterval) -> String {
        let totalMinutes = max(0, Int(seconds / 60))
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours > 0 && minutes > 0 { return "\(hours)時間\(minutes)分遅れ" }
        if hours > 0 { return "\(hours)時間遅れ" }
        return "\(minutes)分遅れ"
    }
}
