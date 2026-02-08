import SwiftUI

struct MedicationFormView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: MedicationFormViewModel
    @State private var hasEndDate = false
    @State private var showingDeleteConfirm = false
    private let onSuccess: ((String) -> Void)?
    private let dosageUnits = ["", "不明", "mg", "g", "mcg", "mL"]

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
        let weekdayColumns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)
        let timeColumns = [
            GridItem(.flexible(), spacing: 12),
            GridItem(.flexible(), spacing: 12)
        ]
        Form {
            Section(NSLocalizedString("medication.form.section.basic", comment: "Basic info")) {
                TextField(NSLocalizedString("medication.form.name", comment: "Medication name"), text: $viewModel.name)
                    .accessibilityLabel("薬名")
                TextField(NSLocalizedString("medication.form.dosage.value", comment: "Dosage value"), text: $viewModel.dosageStrengthValue)
                    .keyboardType(.decimalPad)
                    .disabled(viewModel.dosageStrengthUnit == "不明")
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
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityLabel("服用数")
            }

            if !viewModel.isEditing {
                Section(NSLocalizedString("medication.form.section.prn", comment: "PRN section")) {
                    Toggle(
                        NSLocalizedString("medication.form.prn.toggle", comment: "PRN toggle"),
                        isOn: $viewModel.isPrn
                    )
                    .accessibilityLabel("頓服設定")
                    if viewModel.isPrn {
                        TextField(
                            NSLocalizedString("medication.form.prn.instructions", comment: "PRN instructions"),
                            text: $viewModel.prnInstructions
                        )
                        .accessibilityLabel("頓服の説明")
                    }
                }
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

            if !viewModel.isPrn {
                Section(NSLocalizedString("medication.form.section.schedule", comment: "Schedule")) {
                    Picker(
                        NSLocalizedString("medication.form.schedule.frequency", comment: "Schedule frequency"),
                        selection: $viewModel.scheduleFrequency
                    ) {
                        Text(NSLocalizedString("medication.form.schedule.daily", comment: "Daily")).tag(ScheduleFrequency.daily)
                        Text(NSLocalizedString("medication.form.schedule.weekly", comment: "Weekly")).tag(ScheduleFrequency.weekly)
                    }
                    .pickerStyle(.segmented)

                    if viewModel.scheduleFrequency == .weekly {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(NSLocalizedString("medication.form.schedule.weekdays", comment: "Weekdays"))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            LazyVGrid(columns: weekdayColumns, spacing: 8) {
                                ForEach(ScheduleDay.allCases) { day in
                                    let isSelected = viewModel.selectedDays.contains(day)
                                    Button {
                                        if isSelected {
                                            viewModel.selectedDays.remove(day)
                                        } else {
                                            viewModel.selectedDays.insert(day)
                                        }
                                    } label: {
                                        Text(day.shortLabel)
                                            .font(.body.weight(.semibold))
                                            .frame(maxWidth: .infinity, minHeight: 36)
                                            .background(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .fill(isSelected ? Color.accentColor : Color.primary.opacity(0.05))
                                            )
                                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("曜日 \(day.shortLabel)")
                                    .accessibilityValue(isSelected ? "選択中" : "未選択")
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(NSLocalizedString("medication.form.schedule.times", comment: "Times"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        LazyVGrid(columns: timeColumns, spacing: 12) {
                            ForEach(ScheduleTimeSlot.allCases) { slot in
                                let isSelected = viewModel.selectedTimeSlots.contains(slot)
                                Button {
                                    if isSelected {
                                        viewModel.selectedTimeSlots.remove(slot)
                                    } else {
                                        viewModel.selectedTimeSlots.insert(slot)
                                    }
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(slot.label)
                                                .font(.body.weight(.semibold))
                                            Text(viewModel.timeValue(for: slot))
                                                .font(.footnote)
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .padding(.horizontal, 12)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.primary.opacity(0.05))
                                    )
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("\(slot.label) \(viewModel.timeValue(for: slot))")
                                .accessibilityValue(isSelected ? "選択中" : "未選択")
                            }
                        }
                    }

                    if viewModel.scheduleIsLoading {
                        ProgressView()
                    } else if viewModel.scheduleNotSet && viewModel.isEditing {
                        Text(NSLocalizedString("medication.form.schedule.unset", comment: "Schedule not set"))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            if !viewModel.isEditing {
                Section(NSLocalizedString("medication.form.section.inventory", comment: "Inventory")) {
                    Stepper(
                        value: intBinding(for: $viewModel.inventoryCount),
                        in: 0...9999
                    ) {
                        HStack {
                            Text(NSLocalizedString("medication.form.inventory.count", comment: "Inventory count"))
                            Spacer()
                            Text(viewModel.inventoryCount.isEmpty ? "0" : viewModel.inventoryCount)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityLabel("在庫数")
                }
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
                        .foregroundStyle(.secondary)
                }
            }

            if sessionStore.mode == .patient {
                Section {
                    Text(NSLocalizedString("medication.form.patient.readonly", comment: "Read-only message"))
                        .foregroundStyle(.secondary)
                }
            } else {
                if viewModel.isEditing {
                    HStack(spacing: 12) {
                        saveButton(isCaregiverMissingPatient: isCaregiverMissingPatient)
                        deleteButton(isCaregiverMissingPatient: isCaregiverMissingPatient)
                    }
                    .listRowBackground(Color.clear)
                } else {
                    saveButton(isCaregiverMissingPatient: isCaregiverMissingPatient)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color(.systemGroupedBackground))
        .disabled(sessionStore.mode == .patient)
        .accessibilityIdentifier("MedicationFormView")
        .safeAreaInset(edge: .bottom) {
            Color.clear.frame(height: 24)
        }
        .onAppear {
            hasEndDate = viewModel.endDate != nil
            Task {
                await viewModel.loadExistingScheduleIfNeeded()
            }
        }
        .onChange(of: hasEndDate) { _, enabled in
            if !enabled {
                viewModel.endDate = nil
            } else if viewModel.endDate == nil {
                viewModel.endDate = viewModel.startDate
            }
        }
        .onChange(of: viewModel.scheduleFrequency) { _, frequency in
            if frequency == .daily {
                viewModel.selectedDays = []
            }
        }
        .onChange(of: viewModel.isPrn) { _, isPrn in
            if isPrn {
                viewModel.selectedDays = []
                viewModel.selectedTimeSlots = []
                viewModel.scheduleFrequency = .daily
                viewModel.scheduleNotSet = false
            }
        }
        .onChange(of: viewModel.dosageStrengthUnit) { _, unit in
            if unit == "不明" {
                viewModel.dosageStrengthValue = ""
            }
        }
        .alert(
            NSLocalizedString("medication.form.delete.confirm.title", comment: "Delete confirm title"),
            isPresented: $showingDeleteConfirm
        ) {
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
            Button(NSLocalizedString("medication.form.delete.confirm.action", comment: "Delete confirm action"), role: .destructive) {
                Task {
                    let deleted = await viewModel.deleteMedication()
                    if deleted {
                        NotificationCenter.default.post(name: .medicationUpdated, object: nil)
                        onSuccess?(NSLocalizedString("medication.toast.deleted", comment: "Medication deleted toast"))
                        dismiss()
                    }
                }
            }
        } message: {
            Text(NSLocalizedString("medication.form.delete.confirm.message", comment: "Delete confirm message"))
        }
        .overlay {
            if viewModel.isSubmitting || viewModel.isDeleting {
                updatingOverlay
            }
        }
    }

    @ViewBuilder
    private func saveButton(isCaregiverMissingPatient: Bool) -> some View {
        Button(viewModel.isSubmitting ? "保存中..." : "保存") {
            Task {
                let saved = await viewModel.submit()
                if saved {
                    let messageKey = viewModel.isEditing
                        ? "medication.toast.updated"
                        : "medication.toast.created"
                    NotificationCenter.default.post(name: .medicationUpdated, object: nil)
                    onSuccess?(NSLocalizedString(messageKey, comment: "Medication toast"))
                    dismiss()
                }
            }
        }
        .disabled(viewModel.isSubmitting || isCaregiverMissingPatient)
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .frame(maxWidth: .infinity, alignment: .center)
        .listRowBackground(Color.clear)
        .accessibilityLabel(NSLocalizedString("common.save", comment: "Save"))
    }

    @ViewBuilder
    private func deleteButton(isCaregiverMissingPatient: Bool) -> some View {
        Button(NSLocalizedString("medication.form.delete", comment: "Delete medication")) {
            showingDeleteConfirm = true
        }
        .disabled(viewModel.isDeleting || viewModel.isSubmitting || isCaregiverMissingPatient)
        .buttonStyle(.bordered)
        .tint(.red)
        .controlSize(.large)
        .frame(maxWidth: .infinity, alignment: .center)
        .listRowBackground(Color.clear)
        .accessibilityLabel(NSLocalizedString("medication.form.delete", comment: "Delete medication"))
    }

    private func intBinding(for text: Binding<String>) -> Binding<Int> {
        Binding(
            get: { Int(text.wrappedValue) ?? 0 },
            set: { text.wrappedValue = String(max(0, $0)) }
        )
    }

    private var updatingOverlay: some View {
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
        .accessibilityIdentifier("MedicationFormUpdatingOverlay")
    }
}
