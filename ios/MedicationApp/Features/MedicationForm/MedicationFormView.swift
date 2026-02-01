import SwiftUI

struct MedicationFormView: View {
    @StateObject private var viewModel = MedicationFormViewModel()

    var body: some View {
        Form {
            Section("基本情報") {
                TextField("薬名", text: $viewModel.name)
                TextField("用量 (数値)", text: $viewModel.dosageStrengthValue)
                    .keyboardType(.decimalPad)
                TextField("用量単位", text: $viewModel.dosageStrengthUnit)
                TextField("服用数/回", text: $viewModel.doseCountPerIntake)
                    .keyboardType(.numberPad)
            }

            Section("期間") {
                DatePicker("開始日", selection: $viewModel.startDate, displayedComponents: .date)
                DatePicker(
                    "終了日",
                    selection: Binding(
                        get: { viewModel.endDate ?? viewModel.startDate },
                        set: { viewModel.endDate = $0 }
                    ),
                    displayedComponents: .date
                )
            }

            Section("在庫") {
                TextField("在庫数", text: $viewModel.inventoryCount)
                    .keyboardType(.numberPad)
                TextField("在庫単位", text: $viewModel.inventoryUnit)
            }

            Section("メモ") {
                TextField("メモ", text: $viewModel.notes)
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundColor(.red)
                }
            }

            Button(viewModel.isSubmitting ? "保存中..." : "保存") {
                Task { _ = await viewModel.submit() }
            }
            .disabled(viewModel.isSubmitting)
        }
        .accessibilityIdentifier("MedicationFormView")
    }
}
