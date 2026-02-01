import SwiftUI

struct MedicationFormView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @StateObject private var viewModel = MedicationFormViewModel()

    var body: some View {
        Form {
            Section(NSLocalizedString("medication.form.section.basic", comment: "Basic info")) {
                TextField(NSLocalizedString("medication.form.name", comment: "Medication name"), text: $viewModel.name)
                    .accessibilityLabel("薬名")
                TextField(NSLocalizedString("medication.form.dosage.value", comment: "Dosage value"), text: $viewModel.dosageStrengthValue)
                    .keyboardType(.decimalPad)
                    .accessibilityLabel("用量数値")
                TextField(NSLocalizedString("medication.form.dosage.unit", comment: "Dosage unit"), text: $viewModel.dosageStrengthUnit)
                    .accessibilityLabel("用量単位")
                TextField(NSLocalizedString("medication.form.dose.count", comment: "Dose count"), text: $viewModel.doseCountPerIntake)
                    .keyboardType(.numberPad)
                    .accessibilityLabel("服用数")
            }

            Section(NSLocalizedString("medication.form.section.period", comment: "Period")) {
                DatePicker(NSLocalizedString("medication.form.startDate", comment: "Start date"), selection: $viewModel.startDate, displayedComponents: .date)
                    .accessibilityLabel("開始日")
                DatePicker(
                    NSLocalizedString("medication.form.endDate", comment: "End date"),
                    selection: Binding(
                        get: { viewModel.endDate ?? viewModel.startDate },
                        set: { viewModel.endDate = $0 }
                    ),
                    displayedComponents: .date
                )
                .accessibilityLabel("終了日")
            }

            Section(NSLocalizedString("medication.form.section.inventory", comment: "Inventory")) {
                TextField(NSLocalizedString("medication.form.inventory.count", comment: "Inventory count"), text: $viewModel.inventoryCount)
                    .keyboardType(.numberPad)
                    .accessibilityLabel("在庫数")
                TextField(NSLocalizedString("medication.form.inventory.unit", comment: "Inventory unit"), text: $viewModel.inventoryUnit)
                    .accessibilityLabel("在庫単位")
            }

            Section(NSLocalizedString("medication.form.section.notes", comment: "Notes")) {
                TextField(NSLocalizedString("medication.form.notes", comment: "Notes"), text: $viewModel.notes)
                    .accessibilityLabel("メモ")
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    ErrorStateView(message: errorMessage)
                }
            }

            if sessionStore.mode == .patient {
                Section {
                    Text(NSLocalizedString("medication.form.patient.readonly", comment: "Read-only message"))
                        .foregroundColor(.secondary)
                }
            } else {
                Button(viewModel.isSubmitting ? "保存中..." : "保存") {
                    Task { _ = await viewModel.submit() }
                }
                .disabled(viewModel.isSubmitting)
                .accessibilityLabel(NSLocalizedString("common.save", comment: "Save"))
            }
        }
        .disabled(sessionStore.mode == .patient)
        .accessibilityIdentifier("MedicationFormView")
    }
}
