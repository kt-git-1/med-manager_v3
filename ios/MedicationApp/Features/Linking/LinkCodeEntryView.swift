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
        VStack(spacing: 0) {
            Spacer()

            // Header
            VStack(spacing: 16) {
                Image(systemName: "link.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.tint)
                    .symbolRenderingMode(.hierarchical)

                VStack(spacing: 8) {
                    Text(NSLocalizedString("link.code.title", comment: "Link code title"))
                        .font(.largeTitle.weight(.bold))
                    Text(NSLocalizedString("link.code.subtitle", comment: "Link code subtitle"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }

            Spacer()
                .frame(maxHeight: 40)

            // Code input card
            VStack(spacing: 20) {
                HStack(spacing: 12) {
                    Image(systemName: "number")
                        .foregroundStyle(.secondary)
                        .frame(width: 20)
                    TextField(NSLocalizedString("link.code.placeholder", comment: "Link code placeholder"), text: $code)
                        .keyboardType(.numberPad)
                        .font(.title3.monospacedDigit())
                        .accessibilityLabel("連携コード")
                }
                .padding(14)
                .background(.fill.quaternary)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                if let errorMessage {
                    ErrorStateView(message: errorMessage)
                }

                // Submit button
                Button {
                    Task { await link() }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text(NSLocalizedString("link.code.button", comment: "Send link code"))
                        }
                    }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                }
                .disabled(isLoading || code.isEmpty)
                .opacity(code.isEmpty ? 0.5 : 1)
                .accessibilityLabel("連携コード送信")
            }
            .padding(28)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 24))
            .padding(.horizontal, 24)

            Spacer()

            // Back to mode select
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    sessionStore.resetMode()
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left")
                        .font(.subheadline.weight(.medium))
                    Text(NSLocalizedString("link.code.back", comment: "Back to mode select"))
                        .font(.subheadline)
                }
                .foregroundStyle(.secondary)
            }
            .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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
