import SwiftUI

struct PatientRevokeView: View {
    let patient: PatientDTO
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            // Icon
            Image(systemName: "person.crop.circle.badge.minus")
                .font(.system(size: 52))
                .foregroundStyle(.red)
                .symbolRenderingMode(.hierarchical)

            // Info
            VStack(spacing: 8) {
                Text(NSLocalizedString("caregiver.patients.revoke.confirm.title", comment: "Revoke title"))
                    .font(.title2.weight(.bold))
                Text(patient.displayName)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text(NSLocalizedString("caregiver.patients.revoke.confirm.message", comment: "Revoke message"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            // Buttons
            VStack(spacing: 12) {
                Button {
                    onConfirm()
                } label: {
                    Text(NSLocalizedString("caregiver.patients.revoke.confirm.action", comment: "Confirm revoke"))
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.red, in: RoundedRectangle(cornerRadius: 14))
                }

                Button {
                    onCancel()
                } label: {
                    Text(NSLocalizedString("common.cancel", comment: "Cancel"))
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(.fill.quaternary, in: RoundedRectangle(cornerRadius: 14))
                }
            }
        }
        .padding(28)
        .accessibilityIdentifier("PatientRevokeView")
    }
}
