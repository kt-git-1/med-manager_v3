import SwiftUI

struct MedicationFormView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: MedicationFormViewModel
    @State private var hasEndDate = false
    @State private var showingDeleteConfirm = false
    private let onSuccess: ((String) -> Void)?
    private let dosageUnits = ["", NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"), "mg", "g", "μg", "mL", "IU", "mEq", "%", "滴", "包", "枚", "吸入"]

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
        let activeAccent = viewModel.isPrn ? CaregiverUI.orange : CaregiverUI.teal
        let weekdayColumns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)
        let timeColumns = [
            GridItem(.flexible(), spacing: 12),
            GridItem(.flexible(), spacing: 12)
        ]
        Form {
            formHeroSection(accent: activeAccent)

            // Basic info
            Section {
                formRow(icon: "character.cursor.ibeam", iconColor: .blue) {
                    TextField(NSLocalizedString("medication.form.name", comment: "Medication name"), text: $viewModel.name)
                        .accessibilityLabel(NSLocalizedString("a11y.medication.name", comment: "Name"))
                }
                formRow(icon: "scalemass", iconColor: .orange) {
                    HStack(spacing: 8) {
                        TextField(
                            NSLocalizedString("medication.form.dosage.value", comment: "Dosage value"),
                            text: $viewModel.dosageStrengthValue
                        )
                        .keyboardType(.decimalPad)
                        .disabled(viewModel.dosageStrengthUnit == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"))
                        .accessibilityLabel(NSLocalizedString("a11y.medication.dosageValue", comment: "Dosage value"))

                        Divider()
                            .frame(height: 20)

                        Picker(NSLocalizedString("medication.form.dosage.unit", comment: "Dosage unit"), selection: $viewModel.dosageStrengthUnit) {
                            ForEach(dosageUnits, id: \.self) { unit in
                                Text(unit.isEmpty ? NSLocalizedString("common.select", comment: "Select") : unit).tag(unit)
                            }
                        }
                        .labelsHidden()
                        .fixedSize()
                        .accessibilityLabel(NSLocalizedString("a11y.medication.dosageUnit", comment: "Dosage unit"))
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel(NSLocalizedString("a11y.medication.dosage", comment: "Dosage"))
                }
                HStack(spacing: 12) {
                    formIconLabel(icon: "square.stack.fill", color: .teal)
                    Text(NSLocalizedString("medication.form.dose.count", comment: "Dose count"))
                    Spacer()
                    TextField("0", text: $viewModel.doseCountPerIntake)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 60)
                    Stepper(
                        "",
                        value: decimalBinding(for: $viewModel.doseCountPerIntake),
                        in: 0...999,
                        step: 0.5
                    )
                    .labelsHidden()
                    .fixedSize()
                }
                .accessibilityLabel(NSLocalizedString("a11y.medication.doseCount", comment: "Dose count"))
            } header: {
                sectionHeader(NSLocalizedString("medication.form.section.basic", comment: "Basic info"), icon: "pill.fill")
            } footer: {
                Text(NSLocalizedString("medication.form.help.basic", comment: "Basic section help"))
            }

            medicationTypeSection

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
                    scheduleGuideCard

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
                } footer: {
                    Text(NSLocalizedString("medication.form.help.schedule", comment: "Schedule help"))
                }
            }

            // Inventory
            if !viewModel.isEditing {
                Section {
                    HStack(spacing: 12) {
                        Text(NSLocalizedString("medication.form.inventory.count", comment: "Inventory count"))
                        Spacer()
                        TextField("0", text: $viewModel.inventoryCount)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 60)
                        Stepper(
                            "",
                            value: decimalBinding(for: $viewModel.inventoryCount),
                            in: 0...9999,
                            step: 1
                        )
                        .labelsHidden()
                        .fixedSize()
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
        .background(CaregiverUI.background)
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

    // MARK: - Hero

    private func formHeroSection(accent: Color) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center, spacing: 14) {
                    Image(systemName: viewModel.isEditing ? "pencil.circle.fill" : "pills.circle.fill")
                        .font(.system(size: 42))
                        .symbolRenderingMode(.hierarchical)
                        .foregroundStyle(accent)
                        .frame(width: 54, height: 54)
                        .background(accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(viewModel.isEditing
                            ? viewModel.name.isEmpty ? NSLocalizedString("medication.form.title.edit", comment: "Edit medication") : viewModel.name
                            : NSLocalizedString("medication.form.title.add", comment: "Add medication")
                        )
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.82)
                        Text(viewModel.isPrn
                             ? NSLocalizedString("medication.form.hero.prn", comment: "PRN hero subtitle")
                             : NSLocalizedString("medication.form.hero.scheduled", comment: "Scheduled hero subtitle"))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }

                HStack(spacing: 8) {
                    guidePill(
                        text: NSLocalizedString("medication.form.progress.name", comment: "Name progress"),
                        isComplete: !viewModel.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                        color: CaregiverUI.teal
                    )
                    guidePill(
                        text: NSLocalizedString("medication.form.progress.dose", comment: "Dose progress"),
                        isComplete: !viewModel.dosageStrengthUnit.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                        color: CaregiverUI.blue
                    )
                    guidePill(
                        text: NSLocalizedString("medication.form.progress.schedule", comment: "Schedule progress"),
                        isComplete: viewModel.isPrn || !viewModel.selectedTimeSlots.isEmpty,
                        color: activeScheduleColor
                    )
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(accent.opacity(0.24), lineWidth: 1.2)
            }
            .shadow(color: CaregiverUI.cardShadow, radius: 12, y: 5)
        }
        .listRowInsets(EdgeInsets(top: 16, leading: 16, bottom: 8, trailing: 16))
        .listRowBackground(Color.clear)
    }

    // MARK: - Medication Type

    private var medicationTypeSection: some View {
        Section {
            VStack(spacing: 12) {
                typeChoiceButton(
                    title: NSLocalizedString("medication.form.type.scheduled.title", comment: "Scheduled type title"),
                    subtitle: NSLocalizedString("medication.form.type.scheduled.subtitle", comment: "Scheduled type subtitle"),
                    systemImage: "clock.fill",
                    color: CaregiverUI.teal,
                    isSelected: !viewModel.isPrn
                ) {
                    viewModel.isPrn = false
                }

                typeChoiceButton(
                    title: NSLocalizedString("medication.form.type.prn.title", comment: "PRN type title"),
                    subtitle: NSLocalizedString("medication.form.type.prn.subtitle", comment: "PRN type subtitle"),
                    systemImage: "cross.case.fill",
                    color: CaregiverUI.orange,
                    isSelected: viewModel.isPrn
                ) {
                    viewModel.isPrn = true
                }

                if viewModel.isPrn {
                    formRow(icon: "text.alignleft", iconColor: .gray) {
                        TextField(
                            NSLocalizedString("medication.form.prn.instructions", comment: "PRN instructions"),
                            text: $viewModel.prnInstructions,
                            axis: .vertical
                        )
                        .lineLimit(2...4)
                        .accessibilityLabel(NSLocalizedString("a11y.medication.prnInstructions", comment: "PRN instructions"))
                    }
                    .padding(12)
                    .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(CaregiverUI.cardStroke, lineWidth: 1)
                    }
                }
            }
        } header: {
            sectionHeader(NSLocalizedString("medication.form.section.type", comment: "Medication type section"), icon: "slider.horizontal.3")
        } footer: {
            Text(viewModel.isPrn
                 ? NSLocalizedString("medication.form.help.prn", comment: "PRN help")
                 : NSLocalizedString("medication.form.help.scheduled", comment: "Scheduled help"))
        }
        .listRowBackground(Color.clear)
    }

    private func typeChoiceButton(
        title: String,
        subtitle: String,
        systemImage: String,
        color: Color,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(isSelected ? .white : color)
                    .frame(width: 42, height: 42)
                    .background(isSelected ? color : color.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(isSelected ? color : .secondary)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isSelected ? color.opacity(0.45) : CaregiverUI.cardStroke, lineWidth: isSelected ? 1.5 : 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityValue(isSelected ? NSLocalizedString("a11y.selected", comment: "Selected") : NSLocalizedString("a11y.notSelected", comment: "Not selected"))
    }

    private func guidePill(text: String, isComplete: Bool, color: Color) -> some View {
        HStack(spacing: 5) {
            Image(systemName: isComplete ? "checkmark.circle.fill" : "circle")
                .font(.caption.weight(.bold))
            Text(text)
                .font(.caption.weight(.bold))
                .lineLimit(1)
                .minimumScaleFactor(0.76)
        }
        .foregroundStyle(isComplete ? color : .secondary)
        .padding(.horizontal, 9)
        .padding(.vertical, 6)
        .background((isComplete ? color : Color.secondary).opacity(0.12), in: Capsule())
    }

    private var scheduleGuideCard: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "bell.badge.fill")
                .font(.headline.weight(.bold))
                .foregroundStyle(CaregiverUI.teal)
                .frame(width: 34, height: 34)
                .background(CaregiverUI.teal.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(NSLocalizedString("medication.form.schedule.guide.title", comment: "Schedule guide title"))
                    .font(.headline.weight(.bold))
                Text(NSLocalizedString("medication.form.schedule.guide.message", comment: "Schedule guide message"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .background(CaregiverUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var activeScheduleColor: Color {
        viewModel.isPrn ? CaregiverUI.orange : CaregiverUI.teal
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

    private func decimalBinding(for text: Binding<String>) -> Binding<Double> {
        Binding(
            get: { Double(text.wrappedValue) ?? 0 },
            set: { text.wrappedValue = AppConstants.formatDecimal(max(0, $0)) }
        )
    }

    private var updatingOverlay: some View {
        SchedulingRefreshOverlay()
    }
}
