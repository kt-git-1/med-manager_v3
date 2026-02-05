import SwiftUI

struct SchedulingRefreshOverlay: View {
    var body: some View {
        ZStack {
            Color.black.opacity(0.25)
                .ignoresSafeArea()
            LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                .padding(16)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .shadow(radius: 6)
        }
        .accessibilityIdentifier("SchedulingRefreshOverlay")
    }
}
