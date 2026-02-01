import SwiftUI

struct ModeSelectView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        VStack(spacing: 16) {
            Text("モードを選択")
                .font(.title2)
            Button("家族モード") {
                sessionStore.setMode(.caregiver)
            }
            Button("患者モード") {
                sessionStore.setMode(.patient)
            }
        }
        .padding()
    }
}
