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
    private var hideTask: Task<Void, Never>?

    func handleNotificationRequest(_ request: UNNotificationRequest) {
        handleNotificationIdentifier(request.identifier)
    }

    func handleNotificationIdentifier(_ identifier: String) {
        guard let target = NotificationDeepLinkParser.parse(identifier: identifier) else { return }
        showBanner(slot: target.slot, dateKey: target.dateKey)
    }

    func showBanner(slot: NotificationSlot, dateKey: String) {
        hideTask?.cancel()
        banner = ReminderBanner(slot: slot, dateKey: dateKey, message: slot.notificationBody)
        hideTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3))
            await MainActor.run {
                self?.banner = nil
            }
        }
    }
}

struct ReminderBannerView: View {
    let banner: ReminderBanner

    var body: some View {
        Text(banner.message)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.regularMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(Color(.separator).opacity(0.3)))
            .shadow(color: Color.black.opacity(0.15), radius: 8, y: 4)
            .padding(.top, 10)
            .padding(.horizontal, 16)
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityLabel(banner.message)
    }
}
