import SwiftUI

struct SchedulingRefreshOverlay: View {
    var body: some View {
        ZStack {
            Color.black.opacity(AppConstants.overlayOpacity)
                .ignoresSafeArea()
            VStack(spacing: 12) {
                Image("AppImage")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
            }
            .padding(16)
            .glassEffect(.regular, in: .rect(cornerRadius: 16))
        }
        .accessibilityIdentifier("SchedulingRefreshOverlay")
    }
}
