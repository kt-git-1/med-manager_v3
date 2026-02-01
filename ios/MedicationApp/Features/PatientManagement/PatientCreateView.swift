import SwiftUI

struct PatientCreateView: View {
    @State private var displayName = ""
    @State private var errorMessage: String?

    let onSave: (String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text(NSLocalizedString("caregiver.patients.create.section", comment: "Create section"))) {
                    TextField(
                        NSLocalizedString("caregiver.patients.create.name", comment: "Display name"),
                        text: $displayName
                    )
                    .accessibilityLabel("表示名")
                    if let errorMessage {
                        ErrorStateView(message: errorMessage)
                    }
                }
            }
            .navigationTitle(NSLocalizedString("caregiver.patients.create.title", comment: "Create title"))
            .toolbar {
                Button(NSLocalizedString("common.save", comment: "Save")) {
                    let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else {
                        errorMessage = NSLocalizedString(
                            "caregiver.patients.create.validation",
                            comment: "Validation error"
                        )
                        return
                    }
                    onSave(trimmed)
                }
            }
        }
    }
}
