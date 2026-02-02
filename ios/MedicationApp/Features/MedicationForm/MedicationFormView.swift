import SwiftUI

struct MedicationFormView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: MedicationFormViewModel
    @State private var hasEndDate = false
    private let onSuccess: ((String) -> Void)?
    private let dosageUnits = ["", "mg", "g", "mcg", "mL"]
    private let inventoryUnits = ["", "錠", "包", "本", "個", "mL"]

    init(
        sessionStore: SessionStore? = nil,
        medication: MedicationDTO? = nil,
        onSuccess: ((String) -> Void)? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.onSuccess = onSuccess
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: MedicationFormViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: store),
                sessionStore: store,
                existingMedication: medication
            )
        )
    }

    var body: some View {
        let isCaregiverMissingPatient = sessionStore.mode == .caregiver && sessionStore.currentPatientId == nil
        Form {
            Section(NSLocalizedString("medication.form.section.basic", comment: "Basic info")) {
                TextField(NSLocalizedString("medication.form.name", comment: "Medication name"), text: $viewModel.name)
                    .accessibilityLabel("薬名")
                TextField(NSLocalizedString("medication.form.dosage.value", comment: "Dosage value"), text: $viewModel.dosageStrengthValue)
                    .keyboardType(.decimalPad)
                    .accessibilityLabel("用量数値")
                Picker(NSLocalizedString("medication.form.dosage.unit", comment: "Dosage unit"), selection: $viewModel.dosageStrengthUnit) {
                    ForEach(dosageUnits, id: \.self) { unit in
                        Text(unit.isEmpty ? NSLocalizedString("common.select", comment: "Select") : unit).tag(unit)
                    }
                }
                .accessibilityLabel("用量単位")
                Stepper(
                    value: intBinding(for: $viewModel.doseCountPerIntake),
                    in: 0...999
                ) {
                    HStack {
                        Text(NSLocalizedString("medication.form.dose.count", comment: "Dose count"))
                        Spacer()
                        Text(viewModel.doseCountPerIntake.isEmpty ? "0" : viewModel.doseCountPerIntake)
                            .foregroundColor(.secondary)
                    }
                }
                .accessibilityLabel("服用数")
            }

            Section(NSLocalizedString("medication.form.section.period", comment: "Period")) {
                DatePicker(NSLocalizedString("medication.form.startDate", comment: "Start date"), selection: $viewModel.startDate, displayedComponents: .date)
                    .accessibilityLabel("開始日")
                Toggle(NSLocalizedString("medication.form.endDate.enabled", comment: "Enable end date"), isOn: $hasEndDate)
                    .accessibilityLabel("終了日を設定")
                if hasEndDate {
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
            }

            Section(NSLocalizedString("medication.form.section.inventory", comment: "Inventory")) {
                Stepper(
                    value: intBinding(for: $viewModel.inventoryCount),
                    in: 0...9999
                ) {
                    HStack {
                        Text(NSLocalizedString("medication.form.inventory.count", comment: "Inventory count"))
                        Spacer()
                        Text(viewModel.inventoryCount.isEmpty ? "0" : viewModel.inventoryCount)
                            .foregroundColor(.secondary)
                    }
                }
                .accessibilityLabel("在庫数")
                Picker(NSLocalizedString("medication.form.inventory.unit", comment: "Inventory unit"), selection: $viewModel.inventoryUnit) {
                    ForEach(inventoryUnits, id: \.self) { unit in
                        Text(unit.isEmpty ? NSLocalizedString("common.select", comment: "Select") : unit).tag(unit)
                    }
                }
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

            if isCaregiverMissingPatient {
                Section {
                    Text(NSLocalizedString("medication.form.patient.required", comment: "Patient required"))
                        .foregroundColor(.secondary)
                }
            }

            if sessionStore.mode == .patient {
                Section {
                    Text(NSLocalizedString("medication.form.patient.readonly", comment: "Read-only message"))
                        .foregroundColor(.secondary)
                }
            } else {
                Button(viewModel.isSubmitting ? "保存中..." : "保存") {
                    Task {
                        let saved = await viewModel.submit()
                        if saved {
                            let messageKey = viewModel.isEditing
                                ? "medication.toast.updated"
                                : "medication.toast.created"
                            onSuccess?(NSLocalizedString(messageKey, comment: "Medication toast"))
                            dismiss()
                        }
                    }
                }
                .disabled(viewModel.isSubmitting || isCaregiverMissingPatient)
                .accessibilityLabel(NSLocalizedString("common.save", comment: "Save"))
            }
        }
        .disabled(sessionStore.mode == .patient)
        .accessibilityIdentifier("MedicationFormView")
        .onAppear {
            hasEndDate = viewModel.endDate != nil
        }
        .onChange(of: hasEndDate) { _, enabled in
            if !enabled {
                viewModel.endDate = nil
            } else if viewModel.endDate == nil {
                viewModel.endDate = viewModel.startDate
            }
        }
    }

    private func intBinding(for text: Binding<String>) -> Binding<Int> {
        Binding(
            get: { Int(text.wrappedValue) ?? 0 },
            set: { text.wrappedValue = String(max(0, $0)) }
        )
    }
}
