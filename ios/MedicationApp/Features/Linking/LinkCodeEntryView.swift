import SwiftUI

struct LinkCodeEntryView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var code = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let linkingService: LinkingService

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        self.linkingService = LinkingService(sessionStore: store)
    }

    var body: some View {
        VStack(spacing: 12) {
            Text(NSLocalizedString("link.code.title", comment: "Link code title"))
                .font(.title2)
            TextField(NSLocalizedString("link.code.placeholder", comment: "Link code placeholder"), text: $code)
                .keyboardType(.numberPad)
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
            code = ""
        } catch {
            print("LinkCodeEntryView: link failed \(error)")
            if let apiError = error as? APIError {
                switch apiError {
                case .validation:
                    errorMessage = NSLocalizedString("link.code.error.invalid", comment: "Invalid code")
                case .notFound:
                    errorMessage = NSLocalizedString("link.code.error.not_found", comment: "Code not found")
                case .network(let message):
                    errorMessage = message
                default:
                    if let message = apiError.errorDescription {
                        errorMessage = message
                    } else {
                        errorMessage = "Linking failed: \(apiError)"
                    }
                }
            } else if let message = (error as? LocalizedError)?.errorDescription {
                errorMessage = message
            } else {
                errorMessage = "Linking failed: \(error)"
            }
        }
    }

}
