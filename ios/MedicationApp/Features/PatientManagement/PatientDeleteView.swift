import SwiftUI

struct PatientDeleteView: View {
    @Environment(\.dismiss) private var dismiss
    let patient: PatientDTO
    let onConfirm: () async -> Bool
    let onSuccess: ((String) -> Void)?
    let onCancel: () -> Void
    @State private var isDeleting = false

    var body: some View {
        VStack(spacing: 24) {
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

            // Buttons
            VStack(spacing: 12) {
                Button {
                    Task {
                        guard !isDeleting else { return }
                        isDeleting = true
                        let success = await onConfirm()
                        isDeleting = false
                        if success {
                            onSuccess?(NSLocalizedString("caregiver.patients.toast.deleted", comment: "Patient deleted"))
                            dismiss()
                        }
                    }
                } label: {
                    Group {
                        if isDeleting {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text(NSLocalizedString("caregiver.patients.delete.confirm.action", comment: "Confirm delete"))
                        }
                    }
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
        }
        .padding(28)
        .overlay {
            if isDeleting {
                ZStack {
                    Color.black.opacity(0.2)
                        .ignoresSafeArea()
                    VStack {
                        Spacer()
                        LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                            .padding(16)
                            .glassEffect(.regular, in: .rect(cornerRadius: 16))
                        Spacer()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            }
        }
        .accessibilityIdentifier("PatientDeleteView")
    }
}
