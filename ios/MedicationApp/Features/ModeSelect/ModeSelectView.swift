import SwiftUI

struct ModeSelectView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        VStack {
            Spacer(minLength: 0)
            VStack(spacing: 20) {
                Text(NSLocalizedString("mode.select.title", comment: "Mode selection title"))
                    .font(.title2.weight(.semibold))
                VStack(spacing: 14) {
                    Button(NSLocalizedString("mode.select.caregiver", comment: "Caregiver mode")) {
                        sessionStore.setMode(.caregiver)
                    }
                    .buttonStyle(.borderedProminent)
                    .font(.headline)
                    Button(NSLocalizedString("mode.select.patient", comment: "Patient mode")) {
                        sessionStore.setMode(.patient)
                    }
                    .buttonStyle(.bordered)
                    .font(.headline)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
