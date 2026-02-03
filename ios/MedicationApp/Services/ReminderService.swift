import Foundation
import UserNotifications

@MainActor
final class ReminderService {
    private let notificationCenter: UNUserNotificationCenter
    private let timeFormatter: DateFormatter
    private let reminderPrefix = "dose-reminder-"
    private let reminderOffsets: [TimeInterval] = [0, 5 * 60]

    init(notificationCenter: UNUserNotificationCenter = .current()) {
        self.notificationCenter = notificationCenter
        self.timeFormatter = DateFormatter()
        self.timeFormatter.locale = Locale(identifier: "ja_JP")
        self.timeFormatter.dateStyle = .none
        self.timeFormatter.timeStyle = .short
    }

    func scheduleReminders(for doses: [ScheduleDoseDTO]) async {
        await clearExistingReminders()
        let todayDoses = doses.filter { Calendar.current.isDateInToday($0.scheduledAt) }
        guard !todayDoses.isEmpty else { return }
        guard await ensureAuthorization() else { return }
        let now = Date()
        for dose in todayDoses {
            guard dose.effectiveStatus != .taken else { continue }
            await scheduleDoseReminders(dose, now: now)
        }
    }

    private func scheduleDoseReminders(_ dose: ScheduleDoseDTO, now: Date) async {
        let timeText = timeFormatter.string(from: dose.scheduledAt)
        let title = NSLocalizedString("patient.today.reminder.title", comment: "Reminder title")
        let body = String(
            format: NSLocalizedString("patient.today.reminder.body", comment: "Reminder body"),
            dose.medicationSnapshot.name,
            timeText
        )
        for (index, offset) in reminderOffsets.enumerated() {
            let fireDate = dose.scheduledAt.addingTimeInterval(offset)
            guard fireDate > now else { continue }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: Calendar.current.dateComponents(
                    [.year, .month, .day, .hour, .minute, .second],
                    from: fireDate
                ),
                repeats: false
            )
            let request = UNNotificationRequest(
                identifier: reminderIdentifier(for: dose, index: index),
                content: content,
                trigger: trigger
            )
            await addNotification(request)
        }
    }

    private func reminderIdentifier(for dose: ScheduleDoseDTO, index: Int) -> String {
        "\(reminderPrefix)\(dose.key)-\(index)"
    }

    private func ensureAuthorization() async -> Bool {
        let status = await authorizationStatus()
        switch status {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return await requestAuthorization()
        case .denied:
            return false
        @unknown default:
            return false
        }
    }

    private func requestAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            notificationCenter.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                continuation.resume(returning: granted)
            }
        }
    }

    private func clearExistingReminders() async {
        let identifiers = await pendingReminderIdentifiers()
        if !identifiers.isEmpty {
            notificationCenter.removePendingNotificationRequests(withIdentifiers: identifiers)
        }
    }

    private func pendingReminderIdentifiers() async -> [String] {
        await withCheckedContinuation { continuation in
            notificationCenter.getPendingNotificationRequests { requests in
                let identifiers = requests
                    .map(\.identifier)
                    .filter { $0.hasPrefix(self.reminderPrefix) }
                continuation.resume(returning: identifiers)
            }
        }
    }

    private func authorizationStatus() async -> UNAuthorizationStatus {
        await withCheckedContinuation { continuation in
            notificationCenter.getNotificationSettings { settings in
                continuation.resume(returning: settings.authorizationStatus)
            }
        }
    }

    private func addNotification(_ request: UNNotificationRequest) async {
        await withCheckedContinuation { continuation in
            notificationCenter.add(request) { _ in
                continuation.resume()
            }
        }
    }
}
