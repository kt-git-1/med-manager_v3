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
        VStack {
            Spacer(minLength: 0)
            VStack(spacing: 16) {
                Text(NSLocalizedString("link.code.title", comment: "Link code title"))
                    .font(.title2.weight(.semibold))
                TextField(NSLocalizedString("link.code.placeholder", comment: "Link code placeholder"), text: $code)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                    .font(.body)
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
                .buttonStyle(.borderedProminent)
                .font(.headline)
                .disabled(isLoading)
                .accessibilityLabel("連携コード送信")
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color(.systemBackground))
            )
            .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
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
