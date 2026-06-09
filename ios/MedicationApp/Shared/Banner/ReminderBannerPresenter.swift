import SwiftUI
import UserNotifications

struct ReminderBanner: Equatable {
    let slot: NotificationSlot
    let dateKey: String
    let message: String
}

@MainActor
final class ReminderBannerPresenter: ObservableObject {
    @Published private(set) var banner: ReminderBanner?
    private let toastPresenter: ToastPresenter

    init(toastPresenter: ToastPresenter) {
        self.toastPresenter = toastPresenter
    }

    func handleNotificationRequest(_ request: UNNotificationRequest) {
        handleNotificationIdentifier(request.identifier)
    }

    func handleNotificationIdentifier(_ identifier: String) {
        guard let target = NotificationDeepLinkParser.parse(identifier: identifier) else { return }
        showBanner(slot: target.slot, dateKey: target.dateKey)
    }

    func showBanner(slot: NotificationSlot, dateKey: String) {
        banner = ReminderBanner(slot: slot, dateKey: dateKey, message: slot.notificationBody)
        toastPresenter.show(slot.notificationBody, kind: .info, duration: 3)
    }
}
