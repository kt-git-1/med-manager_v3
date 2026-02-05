import Combine
import SwiftUI

@MainActor
final class CaregiverSessionController: ObservableObject {
    private let sessionStore: SessionStore
    private let bannerPresenter: GlobalBannerPresenter
    private let subscriber: CaregiverEventSubscriber
    private var cancellables = Set<AnyCancellable>()
    private var isForeground = false

    init(sessionStore: SessionStore, bannerPresenter: GlobalBannerPresenter) {
        self.sessionStore = sessionStore
        self.bannerPresenter = bannerPresenter
        self.subscriber = CaregiverEventSubscriber(sessionStore: sessionStore)
        bind()
    }

    func updateScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .active:
            isForeground = true
            updateSubscription()
        case .inactive, .background:
            isForeground = false
            subscriber.stop()
        @unknown default:
            isForeground = false
            subscriber.stop()
        }
    }

    private func bind() {
        subscriber.$latestEvent
            .compactMap { $0 }
            .sink { [weak self] event in
                self?.showBanner(for: event)
            }
            .store(in: &cancellables)

        sessionStore.$mode
            .combineLatest(sessionStore.$caregiverToken)
            .sink { [weak self] _, _ in
                self?.updateSubscription()
            }
            .store(in: &cancellables)
    }

    private func updateSubscription() {
        guard isForeground, sessionStore.mode == .caregiver, sessionStore.caregiverToken != nil else {
            subscriber.stop()
            if sessionStore.mode != .caregiver || sessionStore.caregiverToken == nil {
                subscriber.resetForRevokedAccess()
            }
            return
        }
        subscriber.start()
    }

    private func showBanner(for event: DoseRecordEvent) {
        let format = NSLocalizedString(
            "caregiver.banner.withinTime",
            comment: "Caregiver within time banner"
        )
        let message = String(format: format, event.displayName)
        bannerPresenter.show(message: message)
    }
}
