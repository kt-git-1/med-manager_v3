import SwiftUI

struct MedicationFormView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: MedicationFormViewModel
    @State private var hasEndDate = false
    @State private var showingDeleteConfirm = false
    private let onSuccess: ((String) -> Void)?
    private let dosageUnits = ["", NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"), "mg", "g", "mcg", "mL"]

    init(
        sessionStore: SessionStore? = nil,
        medication: MedicationDTO? = nil,
        onSuccess: ((String) -> Void)? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.onSuccess = onSuccess
        let baseURL = SessionStore.resolveBaseURL()
        let prefs = NotificationPreferencesStore()
        if store.mode == .caregiver, let patientId = store.currentPatientId {
            prefs.switchPatient(patientId)
        }
        _viewModel = StateObject(
            wrappedValue: MedicationFormViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: store),
                sessionStore: store,
                existingMedication: medication,
                preferencesStore: prefs
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
            // Header
            Section {
                VStack(spacing: 10) {
                    Image(systemName: viewModel.isEditing ? "pencil.circle.fill" : "pills.circle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                    Text(viewModel.isEditing
                        ? viewModel.name.isEmpty ? NSLocalizedString("medication.form.title.edit", comment: "Edit medication") : viewModel.name
                        : NSLocalizedString("medication.form.title.add", comment: "Add medication")
                    )
                        .font(.title3.weight(.bold))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }

            // Basic info
            Section {
                formRow(icon: "character.cursor.ibeam", iconColor: .blue) {
                    TextField(NSLocalizedString("medication.form.name", comment: "Medication name"), text: $viewModel.name)
                        .accessibilityLabel(NSLocalizedString("a11y.medication.name", comment: "Name"))
                }
                formRow(icon: "number", iconColor: .orange) {
                    TextField(NSLocalizedString("medication.form.dosage.value", comment: "Dosage value"), text: $viewModel.dosageStrengthValue)
                        .keyboardType(.decimalPad)
                        .disabled(viewModel.dosageStrengthUnit == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"))
                        .accessibilityLabel(NSLocalizedString("a11y.medication.dosageValue", comment: "Dosage value"))
                }
                formRow(icon: "scalemass", iconColor: .purple) {
                    Picker(NSLocalizedString("medication.form.dosage.unit", comment: "Dosage unit"), selection: $viewModel.dosageStrengthUnit) {
                        ForEach(dosageUnits, id: \.self) { unit in
                            Text(unit.isEmpty ? NSLocalizedString("common.select", comment: "Select") : unit).tag(unit)
                        }
                    }
                    .accessibilityLabel(NSLocalizedString("a11y.medication.dosageUnit", comment: "Dosage unit"))
                }
                Stepper(
                    value: intBinding(for: $viewModel.doseCountPerIntake),
                    in: 0...999
                ) {
                    HStack {
                        formIconLabel(icon: "square.stack.fill", color: .teal)
                        Text(NSLocalizedString("medication.form.dose.count", comment: "Dose count"))
                        Spacer()
                        Text(viewModel.doseCountPerIntake.isEmpty ? "0" : viewModel.doseCountPerIntake)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityLabel(NSLocalizedString("a11y.medication.doseCount", comment: "Dose count"))
            } header: {
                sectionHeader(NSLocalizedString("medication.form.section.basic", comment: "Basic info"), icon: "pill.fill")
            }

            // PRN
            if !viewModel.isEditing {
                Section {
                    Toggle(
                        NSLocalizedString("medication.form.prn.toggle", comment: "PRN toggle"),
                        isOn: $viewModel.isPrn
                    )
                    .accessibilityLabel(NSLocalizedString("a11y.medication.prn", comment: "PRN toggle"))
                    if viewModel.isPrn {
                        formRow(icon: "text.alignleft", iconColor: .gray) {
                            TextField(
                                NSLocalizedString("medication.form.prn.instructions", comment: "PRN instructions"),
                                text: $viewModel.prnInstructions
                            )
                            .accessibilityLabel(NSLocalizedString("a11y.medication.prnInstructions", comment: "PRN instructions"))
                        }
                    }
                } header: {
                    sectionHeader(NSLocalizedString("medication.form.section.prn", comment: "PRN section"), icon: "cross.case.fill")
                }
            }

            // Period
            Section {
                DatePicker(NSLocalizedString("medication.form.startDate", comment: "Start date"), selection: $viewModel.startDate, displayedComponents: .date)
                    .accessibilityLabel(NSLocalizedString("a11y.medication.startDate", comment: "Start date"))
                Toggle(NSLocalizedString("medication.form.endDate.enabled", comment: "Enable end date"), isOn: $hasEndDate)
                    .accessibilityLabel(NSLocalizedString("a11y.medication.endDate.toggle", comment: "End date toggle"))
                if hasEndDate {
                    DatePicker(
                        NSLocalizedString("medication.form.endDate", comment: "End date"),
                        selection: Binding(
                            get: { viewModel.endDate ?? viewModel.startDate },
                            set: { viewModel.endDate = $0 }
                        ),
                        displayedComponents: .date
                    )
                    .accessibilityLabel(NSLocalizedString("a11y.medication.endDate", comment: "End date"))
                }
            } header: {
                sectionHeader(NSLocalizedString("medication.form.section.period", comment: "Period"), icon: "calendar")
            }

            // Schedule
            if !viewModel.isPrn {
                Section {
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
                                .font(.subheadline.weight(.medium))
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
                                            .font(.body.weight(.bold))
                                            .frame(maxWidth: .infinity, minHeight: 44)
                                            .background(
                                                RoundedRectangle(cornerRadius: 10)
                                                    .fill(isSelected ? Color.accentColor : Color.primary.opacity(0.06))
                                            )
                                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(String(format: NSLocalizedString("a11y.weekday.format", comment: "Weekday"), day.shortLabel))
                                    .accessibilityValue(isSelected ? NSLocalizedString("a11y.selected", comment: "Selected") : NSLocalizedString("a11y.notSelected", comment: "Not selected"))
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(NSLocalizedString("medication.form.schedule.times", comment: "Times"))
                            .font(.subheadline.weight(.medium))
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
                                    HStack(spacing: 10) {
                                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                            .font(.title3)
                                            .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(slot.label)
                                                .font(.body.weight(.semibold))
                                            Text(viewModel.timeValue(for: slot))
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 48)
                                    .padding(.horizontal, 12)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(isSelected ? Color.accentColor.opacity(0.08) : Color.primary.opacity(0.04))
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(isSelected ? Color.accentColor.opacity(0.3) : Color.clear, lineWidth: 1.5)
                                    )
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("\(slot.label) \(viewModel.timeValue(for: slot))")
                                .accessibilityValue(isSelected ? NSLocalizedString("a11y.selected", comment: "Selected") : NSLocalizedString("a11y.notSelected", comment: "Not selected"))
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
                } header: {
                    sectionHeader(NSLocalizedString("medication.form.section.schedule", comment: "Schedule"), icon: "clock.fill")
                }
            }

            // Inventory
            if !viewModel.isEditing {
                Section {
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
                    .accessibilityLabel(NSLocalizedString("a11y.medication.inventory", comment: "Inventory count"))
                } header: {
                    sectionHeader(NSLocalizedString("medication.form.section.inventory", comment: "Inventory"), icon: "archivebox.fill")
                }
            }

            // Notes
            Section {
                formRow(icon: "note.text", iconColor: .orange) {
                    TextField(NSLocalizedString("medication.form.notes", comment: "Notes"), text: $viewModel.notes)
                        .accessibilityLabel(NSLocalizedString("a11y.medication.notes", comment: "Notes"))
                }
            } header: {
                sectionHeader(NSLocalizedString("medication.form.section.notes", comment: "Notes"), icon: "note.text")
            }

            // Errors
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

            // Actions
            if sessionStore.mode == .patient {
                Section {
                    Text(NSLocalizedString("medication.form.patient.readonly", comment: "Read-only message"))
                        .foregroundStyle(.secondary)
                }
            } else {
                Section {
                    saveButton(isCaregiverMissingPatient: isCaregiverMissingPatient)
                    if viewModel.isEditing {
                        deleteButton(isCaregiverMissingPatient: isCaregiverMissingPatient)
                    }
                }
                .listRowBackground(Color.clear)
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
            if unit == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage") {
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

    // MARK: - Section Header

    private func sectionHeader(_ title: String, icon: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.tint)
            Text(title)
        }
        .font(.subheadline)
        .textCase(nil)
    }

    // MARK: - Form Row with Icon

    private func formRow<Content: View>(icon: String, iconColor: Color, @ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(iconColor)
                .frame(width: 20)
            content()
        }
    }

    private func formIconLabel(icon: String, color: Color) -> some View {
        Image(systemName: icon)
            .font(.subheadline)
            .foregroundStyle(color)
            .frame(width: 20)
    }

    // MARK: - Buttons

    @ViewBuilder
    private func saveButton(isCaregiverMissingPatient: Bool) -> some View {
        Button {
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
        } label: {
            Group {
                if viewModel.isSubmitting {
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
        .disabled(viewModel.isSubmitting || isCaregiverMissingPatient)
        .opacity(isCaregiverMissingPatient ? 0.5 : 1)
        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        .accessibilityLabel(NSLocalizedString("common.save", comment: "Save"))
    }

    @ViewBuilder
    private func deleteButton(isCaregiverMissingPatient: Bool) -> some View {
        Button {
            showingDeleteConfirm = true
        } label: {
            Text(NSLocalizedString("medication.form.delete", comment: "Delete medication"))
                .font(.headline)
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.red.opacity(0.15), in: RoundedRectangle(cornerRadius: 14))
        }
        .disabled(viewModel.isDeleting || viewModel.isSubmitting || isCaregiverMissingPatient)
        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
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
