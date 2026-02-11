import Foundation
import UserNotifications

struct NotificationDeepLinkTarget: Equatable, Sendable {
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

    /// Parse a remote push notification userInfo dict into a deep link target.
    /// Expects keys: "type" (must be "DOSE_TAKEN"), "date" (YYYY-MM-DD), "slot" (morning|noon|evening|bedtime).
    static func parseRemotePush(userInfo: [AnyHashable: Any]) -> NotificationDeepLinkTarget? {
        guard let type = userInfo["type"] as? String, type == "DOSE_TAKEN" else { return nil }
        guard let dateKey = userInfo["date"] as? String else { return nil }
        guard let slotString = userInfo["slot"] as? String,
              let slot = NotificationSlot(rawValue: slotString) else { return nil }
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

    /// Route from a remote push notification payload (012-push-foundation).
    func routeFromRemotePush(userInfo: [AnyHashable: Any]) {
        target = NotificationDeepLinkParser.parseRemotePush(userInfo: userInfo)
    }

    /// Route from an already-parsed deep link target. Used when parsing happens
    /// off the main actor to avoid Sendable issues with userInfo dictionaries.
    func routeFromTarget(_ parsedTarget: NotificationDeepLinkTarget) {
        target = parsedTarget
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
        let userInfo = response.notification.request.content.userInfo
        let identifier = response.notification.request.identifier
        let router = self.router

        // Parse remote push target on the current thread to avoid Sendable issues
        let remotePushTarget = NotificationDeepLinkParser.parseRemotePush(userInfo: userInfo)
        let isRemotePush = userInfo["type"] != nil

        DispatchQueue.main.async {
            if isRemotePush, let target = remotePushTarget {
                router.routeFromTarget(target)
            } else if !isRemotePush {
                router.route(identifier: identifier)
            }
        }
        completionHandler()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        let identifier = notification.request.identifier
        let bannerPresenter = self.bannerPresenter

        // Parse remote push target on the current thread to avoid Sendable issues
        let remotePushTarget = NotificationDeepLinkParser.parseRemotePush(userInfo: userInfo)
        let isRemotePush = userInfo["type"] != nil

        DispatchQueue.main.async {
            if isRemotePush, let target = remotePushTarget {
                bannerPresenter.showBanner(slot: target.slot, dateKey: target.dateKey)
            } else if !isRemotePush {
                bannerPresenter.handleNotificationIdentifier(identifier)
            }
        }

        // For remote push, show system banner + sound
        if isRemotePush {
            completionHandler([.banner, .sound])
        } else {
            completionHandler([])
        }
    }
}
