import SwiftUI

struct GlobalBanner: Equatable {
    let message: String
}

@MainActor
final class GlobalBannerPresenter: ObservableObject {
    @Published private(set) var banner: GlobalBanner?
    private var hideTask: Task<Void, Never>?

    func show(message: String) {
        hideTask?.cancel()
        banner = GlobalBanner(message: message)
        hideTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3))
            await MainActor.run {
                self?.banner = nil
            }
        }
    }
}

struct GlobalBannerView: View {
    let banner: GlobalBanner

    var body: some View {
        Text(banner.message)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())
            .shadow(radius: 6)
            .padding(.top, 10)
            .padding(.horizontal, 16)
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityLabel(banner.message)
    }
}
