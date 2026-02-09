import Foundation
import UserNotifications

@MainActor
final class ReminderService {
    private let notificationCenter: UNUserNotificationCenter
    private let calendar: Calendar
    private let dateKeyFormatter: DateFormatter
    private let reminderPrefix = "dose-reminder-"
    private let preferencesStore: NotificationPreferencesStore

    init(
        notificationCenter: UNUserNotificationCenter = .current(),
        preferencesStore: NotificationPreferencesStore = NotificationPreferencesStore()
    ) {
        self.notificationCenter = notificationCenter
        self.preferencesStore = preferencesStore
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        self.calendar = calendar
        let dateKeyFormatter = DateFormatter()
        dateKeyFormatter.calendar = calendar
        dateKeyFormatter.timeZone = calendar.timeZone
        dateKeyFormatter.locale = AppConstants.posixLocale
        dateKeyFormatter.dateFormat = "yyyy-MM-dd"
        self.dateKeyFormatter = dateKeyFormatter
    }

    func scheduleReminders(for doses: [ScheduleDoseDTO]) async {
        await clearExistingReminders()
        let now = Date()
        let todayDoses = doses.filter { calendar.isDate($0.scheduledAt, inSameDayAs: now) }
        guard !todayDoses.isEmpty else { return }
        guard await ensureAuthorization() else { return }

        let slots = Set(todayDoses.compactMap { dose -> NotificationSlot? in
            guard dose.effectiveStatus == .pending || dose.effectiveStatus == .none else { return nil }
            return NotificationSlot.from(date: dose.scheduledAt, timeZone: calendar.timeZone, slotTimes: preferencesStore.slotTimesMap())
        })

        for slot in slots {
            guard let scheduledAt = scheduledDate(for: slot, on: now) else { continue }
            guard scheduledAt > now else { continue }
            await scheduleSlotReminder(
                slot,
                dateKey: dateKey(for: scheduledAt),
                scheduledAt: scheduledAt
            )
        }
    }

    private func scheduleSlotReminder(
        _ slot: NotificationSlot,
        dateKey: String,
        scheduledAt: Date
    ) async {
        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("patient.today.reminder.title", comment: "Reminder title")
        content.body = slot.notificationBody
        content.sound = .default
        let trigger = UNCalendarNotificationTrigger(
            dateMatching: calendar.dateComponents(
                [.year, .month, .day, .hour, .minute, .second],
                from: scheduledAt
            ),
            repeats: false
        )
        let request = UNNotificationRequest(
            identifier: reminderIdentifier(for: dateKey, slot: slot),
            content: content,
            trigger: trigger
        )
        await addNotification(request)
    }

    private func reminderIdentifier(for dateKey: String, slot: NotificationSlot) -> String {
        "\(reminderPrefix)\(dateKey)-\(slot.rawValue)"
    }

    private func scheduledDate(for slot: NotificationSlot, on date: Date) -> Date? {
        var components = calendar.dateComponents([.year, .month, .day], from: date)
        let override = preferencesStore.slotTime(for: slot)
        components.hour = override.hour
        components.minute = override.minute
        components.second = 0
        return calendar.date(from: components)
    }

    private func dateKey(for date: Date) -> String {
        dateKeyFormatter.string(from: date)
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
