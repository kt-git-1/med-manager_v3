import Foundation
import UserNotifications

struct NotificationDeepLinkTarget: Equatable {
    let dateKey: String
    let slot: NotificationSlot
}

enum NotificationDeepLinkParser {
    static func parse(identifier: String) -> NotificationDeepLinkTarget? {
        let components = identifier.split(separator: ":").map(String.init)
        guard components.count == 4, components[0] == "notif" else { return nil }
        let dateKey = components[1]
        let slotRawValue = components[2]
        guard let slot = NotificationSlot(rawValue: slotRawValue) else { return nil }
        return NotificationDeepLinkTarget(dateKey: dateKey, slot: slot)
    }
}

@MainActor
final class NotificationDeepLinkRouter: ObservableObject {
    @Published private(set) var target: NotificationDeepLinkTarget?

    func handleNotificationResponse(_ response: UNNotificationResponse) {
        route(identifier: response.notification.request.identifier)
    }

    func route(identifier: String) {
        target = NotificationDeepLinkParser.parse(identifier: identifier)
    }

    func clear() {
        target = nil
    }
}

final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    private let router: NotificationDeepLinkRouter
    private let bannerPresenter: ReminderBannerPresenter

    init(router: NotificationDeepLinkRouter, bannerPresenter: ReminderBannerPresenter) {
        self.router = router
        self.bannerPresenter = bannerPresenter
        super.init()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let identifier = response.notification.request.identifier
        let router = self.router
        DispatchQueue.main.async {
            router.route(identifier: identifier)
        }
        completionHandler()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let identifier = notification.request.identifier
        let bannerPresenter = self.bannerPresenter
        DispatchQueue.main.async {
            bannerPresenter.handleNotificationIdentifier(identifier)
        }
        completionHandler([])
    }
}
