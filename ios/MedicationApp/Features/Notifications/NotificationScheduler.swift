import Foundation
import UserNotifications

@MainActor
final class NotificationScheduler {
    private let notificationCenter: UNUserNotificationCenter
    private let calendar: Calendar
    private nonisolated static let identifierPrefix = AppConstants.notificationIdentifierPrefix
    /// Legacy prefix used by the now-removed ReminderService; kept for cleanup.
    private nonisolated static let legacyReminderPrefix = "dose-reminder-"

    init(
        notificationCenter: UNUserNotificationCenter = .current(),
        timeZone: TimeZone = AppConstants.defaultTimeZone
    ) {
        self.notificationCenter = notificationCenter
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        self.calendar = calendar
    }

    func schedule(planEntries: [NotificationPlanEntry], now: Date = Date()) async {
        await clearExistingReminders()
        guard await isAuthorized() else { return }

        for entry in planEntries where entry.scheduledAt > now {
            let content = UNMutableNotificationContent()
            content.title = ""
            content.body = entry.body
            content.sound = .default

            let components = calendar.dateComponents(
                [.year, .month, .day, .hour, .minute, .second],
                from: entry.scheduledAt
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(
                identifier: entry.identifier,
                content: content,
                trigger: trigger
            )
            await addNotification(request)
        }
    }

    func cancelSecondaryReminder(dateKey: String, slot: NotificationSlot) {
        let identifier = "notif:\(dateKey):\(slot.rawValue):2"
        notificationCenter.removePendingNotificationRequests(withIdentifiers: [identifier])
    }

    func cancelAllScheduledNotifications() async {
        await clearExistingReminders()
    }

    private func clearExistingReminders() async {
        let pendingIdentifiers = await pendingReminderIdentifiers()
        if !pendingIdentifiers.isEmpty {
            notificationCenter.removePendingNotificationRequests(withIdentifiers: pendingIdentifiers)
        }

        let deliveredIdentifiers = await deliveredReminderIdentifiers()
        if !deliveredIdentifiers.isEmpty {
            notificationCenter.removeDeliveredNotifications(withIdentifiers: deliveredIdentifiers)
        }
    }

    private func pendingReminderIdentifiers() async -> [String] {
        await withCheckedContinuation { continuation in
            notificationCenter.getPendingNotificationRequests { requests in
                let identifiers = requests
                    .map(\.identifier)
                    .filter { Self.isReminderIdentifier($0) }
                continuation.resume(returning: identifiers)
            }
        }
    }

    private func deliveredReminderIdentifiers() async -> [String] {
        await withCheckedContinuation { continuation in
            notificationCenter.getDeliveredNotifications { notifications in
                let identifiers = notifications
                    .map { $0.request.identifier }
                    .filter { Self.isReminderIdentifier($0) }
                continuation.resume(returning: identifiers)
            }
        }
    }

    private nonisolated static func isReminderIdentifier(_ identifier: String) -> Bool {
        identifier.hasPrefix(identifierPrefix) || identifier.hasPrefix(legacyReminderPrefix)
    }

    private func isAuthorized() async -> Bool {
        let status = await authorizationStatus()
        switch status {
        case .authorized, .provisional, .ephemeral:
            return true
        case .denied, .notDetermined:
            return false
        @unknown default:
            return false
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
