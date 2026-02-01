import SwiftUI

struct PatientRevokeView: View {
    let patient: PatientDTO
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(NSLocalizedString("caregiver.patients.revoke.confirm.title", comment: "Revoke title"))
                .font(.title2)
            Text(patient.displayName)
                .font(.headline)
            Text(NSLocalizedString("caregiver.patients.revoke.confirm.message", comment: "Revoke message"))
                .font(.subheadline)
                .multilineTextAlignment(.center)
            HStack(spacing: 12) {
                Button(NSLocalizedString("common.cancel", comment: "Cancel")) {
                    onCancel()
                }
                .buttonStyle(.bordered)
                Button(NSLocalizedString("caregiver.patients.revoke.confirm.action", comment: "Confirm revoke")) {
                    onConfirm()
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
            }
        }
        .padding()
        .accessibilityIdentifier("PatientRevokeView")
    }
}
