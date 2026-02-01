import SwiftUI

struct CaregiverLoginView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var email = ""
    @State private var password = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let authService = AuthService()

    var body: some View {
        VStack(spacing: 12) {
            Text("家族ログイン")
                .font(.title2)
            TextField("Email", text: $email)
                .textInputAutocapitalization(.never)
                .keyboardType(.emailAddress)
            SecureField("Password", text: $password)
            if let errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
            }
            Button(isLoading ? "ログイン中..." : "ログイン") {
                Task { await login() }
            }
            .disabled(isLoading)
        }
        .padding()
    }

    @MainActor
    private func login() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let token = try await authService.login(email: email, password: password)
            sessionStore.saveCaregiverToken(token)
        } catch {
            errorMessage = "ログインに失敗しました"
        }
    }
}
