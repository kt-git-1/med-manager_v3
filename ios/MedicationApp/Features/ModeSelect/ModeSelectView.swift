import SwiftUI

struct ModeSelectView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        VStack(spacing: 16) {
            Text(NSLocalizedString("mode.select.title", comment: "Mode selection title"))
                .font(.title2)
            Button(NSLocalizedString("mode.select.caregiver", comment: "Caregiver mode")) {
                sessionStore.setMode(.caregiver)
            }
            Button(NSLocalizedString("mode.select.patient", comment: "Patient mode")) {
                sessionStore.setMode(.patient)
            }
        }
        .padding()
    }
}
