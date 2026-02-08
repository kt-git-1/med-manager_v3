import SwiftUI

struct GlobalBanner: Equatable {
    let message: String
}

@MainActor
final class GlobalBannerPresenter: ObservableObject {
    @Published private(set) var banner: GlobalBanner?
    private var hideTask: Task<Void, Never>?

    func show(message: String, duration: TimeInterval = 3) {
        hideTask?.cancel()
        banner = GlobalBanner(message: message)
        hideTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(duration))
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
            .background(Color.white, in: Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.yellow.opacity(0.9), lineWidth: 2)
            )
            .shadow(color: Color.black.opacity(0.12), radius: 10, y: 6)
            .padding(.top, 8)
            .padding(.horizontal, 16)
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityLabel(banner.message)
    }
}
