import Foundation
import UserNotifications

@MainActor
final class NotificationPermissionManager: ObservableObject {
    @Published private(set) var status: UNAuthorizationStatus = .notDetermined

    private let notificationCenter: UNUserNotificationCenter

    init(notificationCenter: UNUserNotificationCenter = .current()) {
        self.notificationCenter = notificationCenter
    }

    func refreshStatus() async {
        status = await fetchStatus()
    }

    func requestAuthorizationIfNeeded() async -> Bool {
        let currentStatus = await fetchStatus()
        status = currentStatus

        switch currentStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .denied:
            return false
        case .notDetermined:
            let granted = await requestAuthorization()
            status = await fetchStatus()
            return granted
        @unknown default:
            return false
        }
    }

    private func fetchStatus() async -> UNAuthorizationStatus {
        await withCheckedContinuation { continuation in
            notificationCenter.getNotificationSettings { settings in
                continuation.resume(returning: settings.authorizationStatus)
            }
        }
    }

    private func requestAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            notificationCenter.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                continuation.resume(returning: granted)
            }
        }
    }
}
