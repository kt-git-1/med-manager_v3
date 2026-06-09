import SwiftUI

@MainActor
final class GlobalBannerPresenter: ObservableObject {
    private let toastPresenter: ToastPresenter

    init(toastPresenter: ToastPresenter) {
        self.toastPresenter = toastPresenter
    }

    func show(message: String, duration: TimeInterval = 3) {
        toastPresenter.show(message, kind: .info, duration: duration)
    }
}
