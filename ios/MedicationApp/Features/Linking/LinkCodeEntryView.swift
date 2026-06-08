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
        ZStack {
            PatientScreenBackground()

            ScrollView {
                VStack(spacing: 22) {
                    PatientHeader(
                        title: NSLocalizedString("link.code.title", comment: "Link code title"),
                        subtitle: NSLocalizedString("link.code.subtitle", comment: "Link code subtitle"),
                        systemImage: "link"
                    )
                    .padding(.top, 48)

                    PatientCard(accent: PatientUI.teal) {
                        VStack(alignment: .leading, spacing: 18) {
                            Text(NSLocalizedString("link.code.placeholder", comment: "Link code placeholder"))
                                .font(.headline.weight(.bold))
                                .foregroundStyle(.primary)

                            HStack(spacing: 12) {
                                Image(systemName: "number")
                                    .font(.title3.weight(.semibold))
                                    .foregroundStyle(PatientUI.teal)
                                    .frame(width: 24)
                                TextField(NSLocalizedString("link.code.placeholder", comment: "Link code placeholder"), text: $code)
                                    .keyboardType(.numberPad)
                                    .font(.title2.monospacedDigit().weight(.semibold))
                                    .foregroundStyle(.primary)
                                    .accessibilityLabel(NSLocalizedString("a11y.linkCode", comment: "Link code"))
                            }
                            .padding(16)
                            .background(PatientUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(PatientUI.teal.opacity(0.18), lineWidth: 1)
                            }

                            if let errorMessage {
                                inlineError(message: errorMessage)
                            }

                            Button {
                                Task { await link() }
                            } label: {
                                Group {
                                    if isLoading {
                                        ProgressView()
                                            .tint(.white)
                                    } else {
                                        Label(NSLocalizedString("link.code.button", comment: "Send link code"), systemImage: "checkmark.circle.fill")
                                    }
                                }
                                .font(.title3.weight(.bold))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 58)
                                .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(isLoading || code.isEmpty)
                            .opacity(code.isEmpty ? 0.55 : 1)
                            .accessibilityLabel(NSLocalizedString("a11y.linkCode.submit", comment: "Submit link code"))
                        }
                    }

                    Button {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            sessionStore.resetMode()
                        }
                    } label: {
                        Label(NSLocalizedString("link.code.back", comment: "Back to mode select"), systemImage: "chevron.left")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(PatientUI.teal)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(PatientUI.teal.opacity(0.18), lineWidth: 1)
                            }
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("LinkCodeEntryView")
    }

    private func inlineError(message: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.headline)
            Text(message)
                .font(.subheadline.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(PatientUI.red)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PatientUI.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
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
                        errorMessage = NSLocalizedString("common.error.linking.generic", comment: "Linking failed")
                    }
                }
            } else if let message = (error as? LocalizedError)?.errorDescription {
                errorMessage = message
            } else {
                errorMessage = NSLocalizedString("common.error.linking.generic", comment: "Linking failed")
            }
        }
    }

}
