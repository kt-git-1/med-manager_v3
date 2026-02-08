import SwiftUI

struct PatientCreateView: View {
    @State private var displayName = ""
    @State private var errorMessage: String?

    let onSave: (String) -> Void

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
                            .accessibilityLabel("表示名")
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
                            onSave(trimmed)
                        } label: {
                            Text(NSLocalizedString("common.save", comment: "Save"))
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                        .opacity(displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)
                    }
                }
                .scrollContentBackground(.hidden)
                .background(Color(.systemGroupedBackground))
            }
            .background(Color(.systemGroupedBackground))
        }
    }
}
