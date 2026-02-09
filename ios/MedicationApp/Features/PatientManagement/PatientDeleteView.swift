import SwiftUI

struct PatientDeleteView: View {
    @Environment(\.dismiss) private var dismiss
    let patient: PatientDTO
    let onConfirm: () async -> Bool
    let onSuccess: ((String) -> Void)?
    let onCancel: () -> Void
    @State private var isDeleting = false
    @State private var showError = false

    var body: some View {
        ZStack {
            VStack(spacing: 24) {
                Spacer()

                // Icon
                Image(systemName: "person.crop.circle.badge.xmark")
                    .font(.system(size: 52))
                    .foregroundStyle(.red)
                    .symbolRenderingMode(.hierarchical)

                // Info
                VStack(spacing: 8) {
                    Text(NSLocalizedString("caregiver.patients.delete.confirm.title", comment: "Delete title"))
                        .font(.title2.weight(.bold))
                    Text(patient.displayName)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(NSLocalizedString("caregiver.patients.delete.confirm.message", comment: "Delete message"))
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
                            guard !isDeleting else { return }
                            showError = false
                            isDeleting = true
                            let success = await onConfirm()
                            isDeleting = false
                            if success {
                                onSuccess?(NSLocalizedString("caregiver.patients.toast.deleted", comment: "Patient deleted"))
                                dismiss()
                            } else {
                                showError = true
                            }
                        }
                    } label: {
                        Text(NSLocalizedString("caregiver.patients.delete.confirm.action", comment: "Confirm delete"))
                            .font(.headline)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.red, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(isDeleting)

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
                    .disabled(isDeleting)
                }

                Spacer()
            }
            .padding(28)

            if isDeleting {
                Color.black.opacity(0.2)
                    .ignoresSafeArea()
                LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                    .padding(16)
                    .glassEffect(.regular, in: .rect(cornerRadius: 16))
            }
        }
        .accessibilityIdentifier("PatientDeleteView")
    }
}
