import SwiftUI

struct SchedulingRefreshOverlay: View {
    var body: some View {
        ZStack {
            Color.black.opacity(0.25)
                .ignoresSafeArea()
            LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                .padding(16)
                .glassEffect(.regular, in: .rect(cornerRadius: 16))
        }
        .accessibilityIdentifier("SchedulingRefreshOverlay")
    }
}
