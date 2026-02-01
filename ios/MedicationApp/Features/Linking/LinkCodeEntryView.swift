import SwiftUI

struct LinkCodeEntryView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var code = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let linkingService = LinkingService()

    var body: some View {
        VStack(spacing: 12) {
            Text("連携コード")
                .font(.title2)
            TextField("コードを入力", text: $code)
            if let errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
            }
            Button(isLoading ? "送信中..." : "送信") {
                Task { await link() }
            }
            .disabled(isLoading)
        }
        .padding()
    }

    @MainActor
    private func link() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let token = try await linkingService.link(code: code)
            sessionStore.savePatientToken(token)
        } catch {
            errorMessage = "連携に失敗しました"
        }
    }
}
