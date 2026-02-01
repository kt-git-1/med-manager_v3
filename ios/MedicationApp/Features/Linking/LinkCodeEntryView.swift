import SwiftUI

struct LinkCodeEntryView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var code = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let linkingService = LinkingService()

    var body: some View {
        VStack(spacing: 12) {
            Text(NSLocalizedString("link.code.title", comment: "Link code title"))
                .font(.title2)
            TextField(NSLocalizedString("link.code.placeholder", comment: "Link code placeholder"), text: $code)
                .accessibilityLabel("連携コード")
            if let errorMessage {
                ErrorStateView(message: errorMessage)
            }
            Button(
                isLoading
                    ? NSLocalizedString("link.code.button.loading", comment: "Sending link code")
                    : NSLocalizedString("link.code.button", comment: "Send link code")
            ) {
                Task { await link() }
            }
            .disabled(isLoading)
            .accessibilityLabel("連携コード送信")
        }
        .padding()
        .accessibilityIdentifier("LinkCodeEntryView")
    }

    @MainActor
    private func link() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let token = try await linkingService.link(code: code)
            sessionStore.savePatientToken(token)
        } catch {
            errorMessage = NSLocalizedString("common.error.linking", comment: "Linking failed")
        }
    }
}
