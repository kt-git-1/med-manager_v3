import SwiftUI

struct PatientRevokeView: View {
    @Environment(\.dismiss) private var dismiss
    let patient: PatientDTO
    let onConfirm: () async -> Bool
    let onSuccess: ((String) -> Void)?
    let onCancel: () -> Void
    @State private var isRevoking = false
    @State private var showError = false

    var body: some View {
        ZStack {
            VStack(spacing: 24) {
                Spacer()

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

                if showError {
                    Text(NSLocalizedString("common.error.generic", comment: "Generic error"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.red)
                }

                // Buttons
                VStack(spacing: 12) {
                    Button {
                        Task {
                            guard !isRevoking else { return }
                            showError = false
                            isRevoking = true
                            let success = await onConfirm()
                            isRevoking = false
                            if success {
                                onSuccess?(NSLocalizedString("caregiver.patients.toast.revoked", comment: "Patient revoked"))
                                dismiss()
                            } else {
                                showError = true
                            }
                        }
                    } label: {
                        Text(NSLocalizedString("caregiver.patients.revoke.confirm.action", comment: "Confirm revoke"))
                            .font(.headline)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.red, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(isRevoking)

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
                    .disabled(isRevoking)
                }

                Spacer()
            }
            .padding(28)

            if isRevoking {
                SchedulingRefreshOverlay()
            }
        }
        .accessibilityIdentifier("PatientRevokeView")
    }
}
