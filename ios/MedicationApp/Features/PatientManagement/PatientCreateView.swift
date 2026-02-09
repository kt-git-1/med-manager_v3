import SwiftUI

struct PatientCreateView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var displayName = ""
    @State private var errorMessage: String?
    @State private var isSaving = false

    let onSave: (String) async -> Bool
    let onSuccess: ((String) -> Void)?

    init(
        onSave: @escaping (String) async -> Bool,
        onSuccess: ((String) -> Void)? = nil
    ) {
        self.onSave = onSave
        self.onSuccess = onSuccess
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: "person.badge.plus.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                    Text(NSLocalizedString("caregiver.patients.create.title", comment: "Create title"))
                        .font(.title2.weight(.bold))
                }
                .padding(.top, 24)
                .padding(.bottom, 8)

                Form {
                    Section {
                        HStack(spacing: 12) {
                            Image(systemName: "person.fill")
                                .font(.subheadline)
                                .foregroundStyle(.blue)
                                .frame(width: 20)
                            TextField(
                                NSLocalizedString("caregiver.patients.create.name", comment: "Display name"),
                                text: $displayName
                            )
                            .accessibilityLabel(NSLocalizedString("a11y.patient.displayName", comment: "Display name"))
                        }
                        if let errorMessage {
                            ErrorStateView(message: errorMessage)
                        }
                    } header: {
                        HStack(spacing: 6) {
                            Image(systemName: "person.text.rectangle")
                                .font(.subheadline)
                                .foregroundStyle(.tint)
                            Text(NSLocalizedString("caregiver.patients.create.section", comment: "Create section"))
                        }
                        .font(.subheadline)
                        .textCase(nil)
                    }

                    Section {
                        Button {
                            let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !trimmed.isEmpty else {
                                errorMessage = NSLocalizedString(
                                    "caregiver.patients.create.validation",
                                    comment: "Validation error"
                                )
                                return
                            }
                            Task {
                                guard !isSaving else { return }
                                isSaving = true
                                let success = await onSave(trimmed)
                                isSaving = false
                                if success {
                                    onSuccess?(NSLocalizedString("caregiver.patients.toast.created", comment: "Patient created"))
                                    dismiss()
                                } else {
                                    errorMessage = NSLocalizedString("common.error.save", comment: "Save error")
                                }
                            }
                        } label: {
                            Group {
                                if isSaving {
                                    ProgressView()
                                        .tint(.white)
                                } else {
                                    Text(NSLocalizedString("common.save", comment: "Save"))
                                }
                            }
                            .font(.headline)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .disabled(isSaving || displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                        .opacity(displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)
                    }
                }
                .scrollContentBackground(.hidden)
                .background(Color(.systemGroupedBackground))
            }
            .background(Color(.systemGroupedBackground))
            .overlay {
                if isSaving {
                    SchedulingRefreshOverlay()
                }
            }
        }
    }
}
