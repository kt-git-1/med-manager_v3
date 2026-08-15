import SwiftUI

struct MedicationFormView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: MedicationFormViewModel
    @State private var hasEndDate = false
    @State private var showingDeleteConfirm = false
    @State private var showsAdditionalSettings = false
    @State private var showsValidationErrors = false
    private let onSuccess: ((String) -> Void)?
    private let dosageUnits = ["", NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"), "mg", "g", "μg", "mL", "IU", "mEq", "%", "滴", "包", "枚", "吸入"]

    init(
        sessionStore: SessionStore? = nil,
        medication: MedicationDTO? = nil,
        marketingPreview: Bool = false,
        onSuccess: ((String) -> Void)? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.onSuccess = onSuccess
        let baseURL = SessionStore.resolveBaseURL()
        let prefs = NotificationPreferencesStore()
        if store.mode == .caregiver, let patientId = store.currentPatientId {
            prefs.switchPatient(patientId)
        }
        let viewModel = MedicationFormViewModel(
            apiClient: APIClient(baseURL: baseURL, sessionStore: store),
            sessionStore: store,
            existingMedication: medication,
            preferencesStore: prefs
        )
        #if targetEnvironment(simulator)
        if marketingPreview || ProcessInfo.processInfo.arguments.contains("-MedicationFormValidationPreview") {
            viewModel.name = "血圧の薬"
            viewModel.dosageStrengthValue = "5"
            viewModel.dosageStrengthUnit = "mg"
            viewModel.doseCountPerIntake = "1"
            viewModel.supplyDays = ProcessInfo.processInfo.arguments.contains("-MedicationFormValidationPreview") ? "14" : "30"
            viewModel.selectedTimeSlots = ProcessInfo.processInfo.arguments.contains("-MedicationFormValidationPreview")
                ? [.morning, .noon, .evening]
                : [.morning, .evening]
            if ProcessInfo.processInfo.arguments.contains("-MedicationFormValidationPreview") {
                viewModel.dosageStrengthUnit = ""
            }
            viewModel.recalculateInventoryFromSupplyDays()
            viewModel.notes = "朝食後・夕食後"
        }
        #endif
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    var body: some View {
        let isCaregiverMissingPatient = sessionStore.mode == .caregiver && sessionStore.currentPatientId == nil
        ScrollViewReader { scrollProxy in
            ScrollView {
                VStack(spacing: 10) {
                    referenceHeader
                    referenceBasicCard
                        .id(MedicationFormScrollTarget.basic)

                    if !viewModel.isPrn {
                        referenceScheduleCard
                            .id(MedicationFormScrollTarget.schedule)
                    }

                    if !viewModel.isEditing {
                        if viewModel.isPrn {
                            referenceManualInventoryCard
                        } else {
                            referenceSupplyCalculatorCard
                        }
                    }

                    if let errorMessage = viewModel.errorMessage {
                        compactFormError(message: errorMessage)
                            .padding(.horizontal, 16)
                    }

                    if sessionStore.mode != .patient {
                        VStack {
                            saveButton(
                                isCaregiverMissingPatient: isCaregiverMissingPatient,
                                scrollProxy: scrollProxy
                            )
                        }
                        .padding(.horizontal, 16)
                    }

                    additionalSettingsCard
                        .id(MedicationFormScrollTarget.additional)

                    if isCaregiverMissingPatient {
                        Text(NSLocalizedString("medication.form.patient.required", comment: "Patient required"))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 18)
                    }

                    if sessionStore.mode == .patient {
                        Text(NSLocalizedString("medication.form.patient.readonly", comment: "Read-only message"))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 18)
                    } else if viewModel.isEditing {
                        VStack {
                            deleteButton(isCaregiverMissingPatient: isCaregiverMissingPatient)
                        }
                        .padding(.horizontal, 18)
                    }
                }
                .padding(.top, 6)
                .padding(.bottom, 24)
            }
        }
        .navigationTitle("")
        .navigationBarHidden(true)
        .background(CaregiverUI.background)
        .disabled(sessionStore.mode == .patient)
        .accessibilityIdentifier("MedicationFormView")
        .onAppear {
            hasEndDate = viewModel.endDate != nil
            viewModel.recalculateInventoryFromSupplyDays()
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
            viewModel.recalculateInventoryFromSupplyDays()
        }
        .onChange(of: viewModel.isPrn) { _, isPrn in
            if isPrn {
                viewModel.selectedDays = []
                viewModel.selectedTimeSlots = []
                viewModel.scheduleFrequency = .daily
                viewModel.scheduleNotSet = false
                if !viewModel.isEditing {
                    viewModel.supplyDays = ""
                    viewModel.inventoryCount = ""
                }
            }
            viewModel.recalculateInventoryFromSupplyDays()
        }
        .onChange(of: viewModel.dosageStrengthUnit) { _, unit in
            if unit == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage") {
                viewModel.dosageStrengthValue = ""
            }
        }
        .onChange(of: viewModel.supplyDays) { _, _ in viewModel.recalculateInventoryFromSupplyDays() }
        .onChange(of: viewModel.doseCountPerIntake) { _, _ in viewModel.recalculateInventoryFromSupplyDays() }
        .onChange(of: viewModel.selectedTimeSlots) { _, _ in viewModel.recalculateInventoryFromSupplyDays() }
        .onChange(of: viewModel.selectedDays) { _, _ in viewModel.recalculateInventoryFromSupplyDays() }
        .onChange(of: viewModel.startDate) { _, _ in viewModel.recalculateInventoryFromSupplyDays() }
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

    // MARK: - Reference-aligned registration flow

    private var referenceHeader: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 3) {
                Text(viewModel.isEditing
                     ? NSLocalizedString("medication.form.title.edit", comment: "Edit medication title")
                     : NSLocalizedString("medication.form.title.register", comment: "Register medication title"))
                    .font(.system(size: 22, weight: .bold))

                Text(NSLocalizedString("medication.form.register.guide", comment: "Registration guide"))
                    .font(.system(size: 15, weight: .medium))
                    .padding(.top, 6)
            }
            .frame(maxWidth: .infinity)

            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundStyle(CaregiverUI.teal)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(NSLocalizedString("common.close", comment: "Close"))
                Spacer()
            }
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 4)
    }

    private var referenceBasicCard: some View {
        referenceCard {
            referenceCardHeader(
                NSLocalizedString("medication.form.section.basic", comment: "Basic information"),
                icon: "doc.text.fill",
                accent: CaregiverUI.teal
            )

            VStack(alignment: .leading, spacing: 7) {
                Text(NSLocalizedString("medication.form.name", comment: "Medication name"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)

                HStack(spacing: 8) {
                    TextField(NSLocalizedString("medication.form.name.placeholder", comment: "Medication name placeholder"), text: $viewModel.name)
                        .font(.system(size: 18, weight: .bold))
                        .layoutPriority(1)
                        .accessibilityLabel(NSLocalizedString("a11y.medication.name", comment: "Name"))

                    TextField("5", text: $viewModel.dosageStrengthValue)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 18, weight: .bold))
                        .multilineTextAlignment(.trailing)
                        .frame(width: 48)
                        .disabled(viewModel.dosageStrengthUnit == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"))

                    Picker(NSLocalizedString("medication.form.dosage.unit", comment: "Dosage unit"), selection: $viewModel.dosageStrengthUnit) {
                        ForEach(dosageUnits, id: \.self) { unit in
                            Text(unit.isEmpty ? NSLocalizedString("common.select", comment: "Select") : unit).tag(unit)
                        }
                    }
                    .labelsHidden()
                    .fixedSize()
                }
                .padding(.horizontal, 14)
                .frame(minHeight: 40)
                .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(
                            basicValidationMessages.isEmpty ? CaregiverUI.cardStroke : CaregiverUI.red,
                            lineWidth: basicValidationMessages.isEmpty ? 1.2 : 2
                        )
                }

                if !basicValidationMessages.isEmpty {
                    inlineValidationMessages(
                        basicValidationMessages,
                        identifier: "MedicationBasicValidationError"
                    )
                }
            }

            Divider()

            VStack(alignment: .leading, spacing: 7) {
                Text(NSLocalizedString("medication.form.dose.count", comment: "Dose count"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)

                HStack(spacing: 12) {
                    TextField("1", text: $viewModel.doseCountPerIntake)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .multilineTextAlignment(.center)
                        .frame(width: 116, height: 40)
                        .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .overlay { RoundedRectangle(cornerRadius: 10).stroke(CaregiverUI.cardStroke, lineWidth: 1.2) }
                    Text(NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                        .font(.system(size: 18, weight: .bold))
                    Spacer()
                }
            }
        }
    }

    private var referenceScheduleCard: some View {
        referenceCard {
            referenceCardHeader(
                NSLocalizedString("medication.form.schedule.timingTitle", comment: "Dose timing"),
                icon: "clock",
                accent: CaregiverUI.orange,
                filled: false
            )

            HStack(spacing: 8) {
                ForEach(ScheduleTimeSlot.allCases) { slot in
                    referenceTimingButton(slot)
                }
            }

            if !scheduleValidationMessages.isEmpty {
                inlineValidationMessages(
                    scheduleValidationMessages,
                    identifier: "MedicationScheduleValidationError"
                )
            }

            Text(String(format: NSLocalizedString("medication.form.schedule.dailyCount", comment: "Daily dose count"), viewModel.dosesPerDay))
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(CaregiverUI.orange)
                .frame(maxWidth: 150)
                .padding(.vertical, 5)
                .background(CaregiverUI.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .frame(maxWidth: .infinity)
        }
    }

    private func referenceTimingButton(_ slot: ScheduleTimeSlot) -> some View {
        let isSelected = viewModel.selectedTimeSlots.contains(slot)
        return Button {
            if isSelected {
                viewModel.selectedTimeSlots.remove(slot)
            } else {
                viewModel.selectedTimeSlots.insert(slot)
            }
        } label: {
            VStack(spacing: 5) {
                Text(slot.label)
                    .font(.system(size: 17, weight: .bold))
                Image(systemName: slotIcon(slot))
                    .font(.system(size: 21, weight: .semibold))
            }
            .foregroundStyle(isSelected ? CaregiverUI.teal : .secondary)
            .frame(maxWidth: .infinity, minHeight: 66)
            .background(isSelected ? CaregiverUI.teal.opacity(0.06) : Color.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isSelected ? CaregiverUI.teal : CaregiverUI.cardStroke, lineWidth: isSelected ? 1.7 : 1.1)
            }
            .overlay(alignment: .topTrailing) {
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 19, weight: .bold))
                        .foregroundStyle(CaregiverUI.teal)
                        .background(Color.white, in: Circle())
                        .offset(x: 6, y: -7)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(slot.label) \(viewModel.timeValue(for: slot))")
        .accessibilityValue(isSelected ? NSLocalizedString("a11y.selected", comment: "Selected") : NSLocalizedString("a11y.notSelected", comment: "Not selected"))
    }

    private var referenceSupplyCalculatorCard: some View {
        referenceCard(background: CaregiverUI.teal.opacity(0.035)) {
            referenceCardHeader(
                NSLocalizedString("medication.form.inventory.calculator.title", comment: "Medication quantity"),
                icon: "calendar",
                accent: CaregiverUI.teal
            )

            VStack(alignment: .leading, spacing: 7) {
                Text(NSLocalizedString("medication.form.inventory.supplyDays.question", comment: "Supply days question"))
                    .font(.system(size: 14, weight: .semibold))

                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    TextField("30", text: $viewModel.supplyDays)
                        .keyboardType(.numberPad)
                        .font(.system(size: 31, weight: .bold, design: .rounded))
                        .foregroundStyle(CaregiverUI.teal)
                        .accessibilityIdentifier("MedicationSupplyDaysField")
                    Text(NSLocalizedString("medication.form.inventory.supplyDays.unit", comment: "Days supply unit"))
                        .font(.system(size: 17, weight: .bold))
                }
                .padding(.horizontal, 16)
                .frame(height: 44)
                .background(Color.white.opacity(0.84), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 10).stroke(CaregiverUI.teal, lineWidth: 1.4) }
            }

            ZStack {
                Divider()
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(CaregiverUI.teal)
                    .font(.title3)
                    .background(backgroundCircle)
            }
            .frame(height: 14)

            HStack(spacing: 7) {
                calculationTerm(AppConstants.formatDecimal(Double(viewModel.doseCountPerIntake) ?? 0) + NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                Text("×").font(.headline.weight(.bold))
                calculationTerm("\(viewModel.dosesPerDay)回")
                Text("×").font(.headline.weight(.bold))
                calculationTerm((viewModel.supplyDays.isEmpty ? "0" : viewModel.supplyDays) + "日")
                Text("=").font(.headline.weight(.bold))
            }

            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(NSLocalizedString("medication.form.inventory.initial", comment: "Initial inventory"))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(CaregiverUI.teal)
                Spacer(minLength: 4)
                TextField("0", text: $viewModel.inventoryCount)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                    .foregroundStyle(CaregiverUI.orange)
                    .frame(minWidth: 92)
                    .accessibilityIdentifier("MedicationCalculatedInventoryField")
                Text(NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(CaregiverUI.orange)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(CaregiverUI.teal.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            Label(NSLocalizedString("medication.form.inventory.calculator.help.short", comment: "Automatic calculation help"), systemImage: "info.circle")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    private var referenceManualInventoryCard: some View {
        referenceCard {
            referenceCardHeader(
                NSLocalizedString("medication.form.section.inventory", comment: "Inventory"),
                icon: "archivebox.fill",
                accent: CaregiverUI.orange
            )
            HStack {
                TextField("0", text: $viewModel.inventoryCount)
                    .keyboardType(.decimalPad)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                Text(NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                    .font(.title3.weight(.bold))
            }
            .padding(.horizontal, 14)
            .frame(height: 56)
            .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 10))
            .overlay { RoundedRectangle(cornerRadius: 10).stroke(CaregiverUI.cardStroke, lineWidth: 1.2) }
        }
    }

    private var additionalSettingsCard: some View {
        DisclosureGroup(isExpanded: $showsAdditionalSettings) {
            VStack(spacing: 14) {
                typeChoiceButton(
                    title: NSLocalizedString("medication.form.type.scheduled.title", comment: "Scheduled type title"),
                    subtitle: NSLocalizedString("medication.form.type.scheduled.subtitle", comment: "Scheduled type subtitle"),
                    systemImage: "clock.fill",
                    color: CaregiverUI.teal,
                    isSelected: !viewModel.isPrn
                ) { viewModel.isPrn = false }

                typeChoiceButton(
                    title: NSLocalizedString("medication.form.type.prn.title", comment: "PRN type title"),
                    subtitle: NSLocalizedString("medication.form.type.prn.subtitle", comment: "PRN type subtitle"),
                    systemImage: "cross.case.fill",
                    color: CaregiverUI.orange,
                    isSelected: viewModel.isPrn
                ) { viewModel.isPrn = true }

                if !viewModel.isPrn {
                    Picker(NSLocalizedString("medication.form.schedule.frequency", comment: "Schedule frequency"), selection: $viewModel.scheduleFrequency) {
                        Text(NSLocalizedString("medication.form.schedule.daily", comment: "Daily")).tag(ScheduleFrequency.daily)
                        Text(NSLocalizedString("medication.form.schedule.weekly", comment: "Weekly")).tag(ScheduleFrequency.weekly)
                    }
                    .pickerStyle(.segmented)

                    if viewModel.scheduleFrequency == .weekly {
                        let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)
                        LazyVGrid(columns: columns, spacing: 6) {
                            ForEach(ScheduleDay.allCases) { day in
                                let isSelected = viewModel.selectedDays.contains(day)
                                Button {
                                    if isSelected { viewModel.selectedDays.remove(day) }
                                    else { viewModel.selectedDays.insert(day) }
                                } label: {
                                    Text(day.shortLabel)
                                        .font(.caption.weight(.bold))
                                        .frame(maxWidth: .infinity, minHeight: 38)
                                        .foregroundStyle(isSelected ? .white : .primary)
                                        .background(isSelected ? CaregiverUI.teal : Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 9))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                } else {
                    TextField(NSLocalizedString("medication.form.prn.instructions", comment: "PRN instructions"), text: $viewModel.prnInstructions, axis: .vertical)
                        .lineLimit(2...4)
                        .textFieldStyle(.roundedBorder)
                }

                Divider()
                DatePicker(NSLocalizedString("medication.form.startDate", comment: "Start date"), selection: $viewModel.startDate, displayedComponents: .date)
                Toggle(NSLocalizedString("medication.form.endDate.enabled", comment: "Enable end date"), isOn: $hasEndDate)
                if hasEndDate {
                    DatePicker(
                        NSLocalizedString("medication.form.endDate", comment: "End date"),
                        selection: Binding(get: { viewModel.endDate ?? viewModel.startDate }, set: { viewModel.endDate = $0 }),
                        displayedComponents: .date
                    )
                }
                TextField(NSLocalizedString("medication.form.notes", comment: "Notes"), text: $viewModel.notes)
                    .textFieldStyle(.roundedBorder)

                if !additionalValidationMessages.isEmpty {
                    inlineValidationMessages(
                        additionalValidationMessages,
                        identifier: "MedicationAdditionalValidationError"
                    )
                }
            }
            .padding(.top, 10)
        } label: {
            Label(NSLocalizedString("medication.form.additionalSettings", comment: "Additional settings"), systemImage: "slider.horizontal.3")
                .font(.headline.weight(.bold))
                .foregroundStyle(.primary)
        }
        .padding(14)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: 16).stroke(CaregiverUI.cardStroke, lineWidth: 1) }
        .padding(.horizontal, 16)
    }

    private func referenceCard<Content: View>(
        background: Color = CaregiverUI.cardBackground,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            content()
        }
        .padding(10)
        .background(background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(CaregiverUI.cardStroke, lineWidth: 1) }
        .shadow(color: CaregiverUI.cardShadow, radius: 8, y: 3)
        .padding(.horizontal, 16)
    }

    private func referenceCardHeader(_ title: String, icon: String, accent: Color, filled: Bool = true) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(filled ? .headline.weight(.bold) : .title2.weight(.semibold))
                .foregroundStyle(filled ? Color.white : accent)
                .frame(width: 28, height: 28)
                .background(filled ? accent : Color.clear, in: Circle())
            Text(title)
                .font(.system(size: 19, weight: .bold))
        }
    }

    private var backgroundCircle: some View {
        Circle()
            .fill(CaregiverUI.cardBackground)
            .frame(width: 24, height: 24)
    }

    private func calculationTerm(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14, weight: .bold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: 9).stroke(CaregiverUI.cardStroke, lineWidth: 1) }
    }

    // MARK: - Previous registration components

    private var refreshedIntroSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(NSLocalizedString("medication.form.progress.step", comment: "Registration step"))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(CaregiverUI.teal)
                    Spacer()
                    Text(viewModel.isPrn
                         ? NSLocalizedString("medication.form.type.prn.title", comment: "PRN type title")
                         : NSLocalizedString("medication.form.type.scheduled.title", comment: "Scheduled type title"))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                }
                Text(NSLocalizedString("medication.form.register.guide", comment: "Registration guide"))
                    .font(.title3.weight(.bold))
                Text(NSLocalizedString("medication.form.register.help", comment: "Registration help"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CaregiverUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .listRowInsets(EdgeInsets(top: 14, leading: 16, bottom: 4, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private var refreshedBasicSection: some View {
        Section {
            registrationCard(
                title: NSLocalizedString("medication.form.section.basic", comment: "Basic information"),
                icon: "doc.text.fill",
                accent: CaregiverUI.teal
            ) {
                labeledField(NSLocalizedString("medication.form.name", comment: "Medication name")) {
                    TextField(NSLocalizedString("medication.form.name.placeholder", comment: "Medication name placeholder"), text: $viewModel.name)
                        .font(.body.weight(.semibold))
                        .accessibilityLabel(NSLocalizedString("a11y.medication.name", comment: "Name"))
                }

                labeledField(NSLocalizedString("medication.form.dosage.label", comment: "Dosage strength label")) {
                    HStack(spacing: 8) {
                        TextField(NSLocalizedString("medication.form.dosage.value", comment: "Dosage value"), text: $viewModel.dosageStrengthValue)
                            .keyboardType(.decimalPad)
                            .disabled(viewModel.dosageStrengthUnit == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage"))
                        Picker(NSLocalizedString("medication.form.dosage.unit", comment: "Dosage unit"), selection: $viewModel.dosageStrengthUnit) {
                            ForEach(dosageUnits, id: \.self) { unit in
                                Text(unit.isEmpty ? NSLocalizedString("common.select", comment: "Select") : unit).tag(unit)
                            }
                        }
                        .fixedSize()
                    }
                }

                labeledField(NSLocalizedString("medication.form.dose.count", comment: "Dose count")) {
                    HStack(spacing: 10) {
                        TextField("0", text: $viewModel.doseCountPerIntake)
                            .keyboardType(.decimalPad)
                            .font(.title3.weight(.bold))
                        Text(NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                            .font(.body.weight(.bold))
                            .foregroundStyle(.secondary)
                        Spacer()
                        Stepper("", value: decimalBinding(for: $viewModel.doseCountPerIntake), in: 0...999, step: 0.5)
                            .labelsHidden()
                    }
                }
            }
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private var refreshedScheduleSection: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 4)
        let weekdayColumns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)

        return Section {
            registrationCard(
                title: NSLocalizedString("medication.form.schedule.times", comment: "Dose timing"),
                icon: "clock.fill",
                accent: CaregiverUI.orange
            ) {
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(ScheduleTimeSlot.allCases) { slot in
                        let isSelected = viewModel.selectedTimeSlots.contains(slot)
                        Button {
                            if isSelected {
                                viewModel.selectedTimeSlots.remove(slot)
                            } else {
                                viewModel.selectedTimeSlots.insert(slot)
                            }
                        } label: {
                            VStack(spacing: 5) {
                                Image(systemName: isSelected ? "checkmark.circle.fill" : slotIcon(slot))
                                    .font(.title2.weight(.bold))
                                Text(slot.label)
                                    .font(.body.weight(.bold))
                                Text(viewModel.timeValue(for: slot))
                                    .font(.caption2.weight(.semibold))
                            }
                            .foregroundStyle(isSelected ? CaregiverUI.teal : .secondary)
                            .frame(maxWidth: .infinity, minHeight: 88)
                            .background(isSelected ? CaregiverUI.teal.opacity(0.1) : Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(isSelected ? CaregiverUI.teal : CaregiverUI.cardStroke, lineWidth: isSelected ? 2 : 1)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(slot.label) \(viewModel.timeValue(for: slot))")
                        .accessibilityValue(isSelected ? NSLocalizedString("a11y.selected", comment: "Selected") : NSLocalizedString("a11y.notSelected", comment: "Not selected"))
                    }
                }

                Text(String(format: NSLocalizedString("medication.form.schedule.dailyCount", comment: "Daily dose count"), viewModel.dosesPerDay))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(CaregiverUI.orange)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(CaregiverUI.orange.opacity(0.1), in: Capsule())

                Picker(NSLocalizedString("medication.form.schedule.frequency", comment: "Schedule frequency"), selection: $viewModel.scheduleFrequency) {
                    Text(NSLocalizedString("medication.form.schedule.daily", comment: "Daily")).tag(ScheduleFrequency.daily)
                    Text(NSLocalizedString("medication.form.schedule.weekly", comment: "Weekly")).tag(ScheduleFrequency.weekly)
                }
                .pickerStyle(.segmented)

                if viewModel.scheduleFrequency == .weekly {
                    LazyVGrid(columns: weekdayColumns, spacing: 6) {
                        ForEach(ScheduleDay.allCases) { day in
                            let isSelected = viewModel.selectedDays.contains(day)
                            Button {
                                if isSelected { viewModel.selectedDays.remove(day) }
                                else { viewModel.selectedDays.insert(day) }
                            } label: {
                                Text(day.shortLabel)
                                    .font(.caption.weight(.bold))
                                    .frame(maxWidth: .infinity, minHeight: 40)
                                    .foregroundStyle(isSelected ? .white : .primary)
                                    .background(isSelected ? CaregiverUI.teal : Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private var refreshedPeriodSection: some View {
        Section {
            registrationCard(
                title: NSLocalizedString("medication.form.section.period", comment: "Period"),
                icon: "calendar",
                accent: CaregiverUI.blue
            ) {
                DatePicker(NSLocalizedString("medication.form.startDate", comment: "Start date"), selection: $viewModel.startDate, displayedComponents: .date)
                Toggle(NSLocalizedString("medication.form.endDate.enabled", comment: "Enable end date"), isOn: $hasEndDate)
                if hasEndDate {
                    DatePicker(
                        NSLocalizedString("medication.form.endDate", comment: "End date"),
                        selection: Binding(get: { viewModel.endDate ?? viewModel.startDate }, set: { viewModel.endDate = $0 }),
                        displayedComponents: .date
                    )
                }
            }
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private var supplyCalculatorSection: some View {
        Section {
            registrationCard(
                title: NSLocalizedString("medication.form.inventory.calculator.title", comment: "Medication quantity"),
                icon: "calendar.badge.clock",
                accent: CaregiverUI.teal
            ) {
                Text(NSLocalizedString("medication.form.inventory.supplyDays.question", comment: "Supply days question"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)

                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    TextField("30", text: $viewModel.supplyDays)
                        .keyboardType(.numberPad)
                        .font(.system(size: 38, weight: .bold, design: .rounded))
                        .foregroundStyle(CaregiverUI.teal)
                        .accessibilityIdentifier("MedicationSupplyDaysField")
                    Text(NSLocalizedString("medication.form.inventory.supplyDays.unit", comment: "Days supply unit"))
                        .font(.title3.weight(.bold))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 14).stroke(CaregiverUI.teal, lineWidth: 1.5) }

                if let formula = viewModel.inventoryCalculationDescription,
                   viewModel.calculatedInventoryCount != nil {
                    Text(formula)
                        .font(.headline.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)

                    HStack(alignment: .firstTextBaseline) {
                        Text(NSLocalizedString("medication.form.inventory.initial", comment: "Initial inventory"))
                            .font(.headline.weight(.bold))
                            .foregroundStyle(CaregiverUI.teal)
                        Spacer()
                        TextField("0", text: $viewModel.inventoryCount)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .font(.system(size: 44, weight: .bold, design: .rounded))
                            .foregroundStyle(CaregiverUI.orange)
                            .frame(minWidth: 90)
                            .accessibilityIdentifier("MedicationCalculatedInventoryField")
                        Text(NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(CaregiverUI.orange)
                    }
                    .padding(14)
                    .background(CaregiverUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                } else {
                    Text(NSLocalizedString("medication.form.inventory.calculator.empty", comment: "Calculation help"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
                }

                Label(NSLocalizedString("medication.form.inventory.calculator.help", comment: "Automatic calculation help"), systemImage: "info.circle")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .background(CaregiverUI.teal.opacity(0.03), in: RoundedRectangle(cornerRadius: 20))
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private var manualInventorySection: some View {
        Section {
            registrationCard(
                title: NSLocalizedString("medication.form.section.inventory", comment: "Inventory"),
                icon: "archivebox.fill",
                accent: CaregiverUI.orange
            ) {
                labeledField(NSLocalizedString("medication.form.inventory.count", comment: "Inventory count")) {
                    HStack {
                        TextField("0", text: $viewModel.inventoryCount)
                            .keyboardType(.decimalPad)
                        Text(NSLocalizedString("common.unit.tablet", comment: "Tablet unit"))
                    }
                }
            }
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private func registrationCard<Content: View>(
        title: String,
        icon: String,
        accent: Color,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(accent, in: Circle())
                Text(title)
                    .font(.title3.weight(.bold))
            }
            content()
        }
        .padding(18)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: 20).stroke(accent.opacity(0.22), lineWidth: 1.2) }
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
    }

    private func labeledField<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            content()
                .padding(.horizontal, 14)
                .frame(minHeight: 52)
                .background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 13).stroke(CaregiverUI.cardStroke, lineWidth: 1) }
        }
    }

    private func slotIcon(_ slot: ScheduleTimeSlot) -> String {
        switch slot {
        case .morning: return "sun.max.fill"
        case .noon: return "sun.haze.fill"
        case .evening: return "moon.stars.fill"
        case .bedtime: return "bed.double.fill"
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

    // MARK: - Validation

    private var visibleValidationMessages: [String] {
        showsValidationErrors ? viewModel.validate() : []
    }

    private var basicValidationMessages: [String] {
        validationMessages(for: [
            "medication.form.validation.name.required",
            "medication.form.validation.dosage.required",
            "medication.form.validation.dosage.value.required"
        ])
    }

    private var scheduleValidationMessages: [String] {
        validationMessages(for: ["medication.form.validation.timeSlot.required"])
    }

    private var additionalValidationMessages: [String] {
        validationMessages(for: [
            "medication.form.validation.endDate.invalid",
            "medication.form.validation.weekday.required"
        ])
    }

    private func validationMessages(for keys: [String]) -> [String] {
        let messages = Set(keys.map { NSLocalizedString($0, comment: "Medication form validation") })
        return visibleValidationMessages.filter(messages.contains)
    }

    private func firstValidationTarget(for messages: [String]) -> MedicationFormScrollTarget? {
        let basicMessages = Set([
            NSLocalizedString("medication.form.validation.name.required", comment: "Name required"),
            NSLocalizedString("medication.form.validation.dosage.required", comment: "Dosage required"),
            NSLocalizedString("medication.form.validation.dosage.value.required", comment: "Dosage value required")
        ])
        if messages.contains(where: basicMessages.contains) {
            return .basic
        }

        let scheduleMessage = NSLocalizedString(
            "medication.form.validation.timeSlot.required",
            comment: "Time slot required"
        )
        if messages.contains(scheduleMessage) {
            return .schedule
        }

        return messages.isEmpty ? nil : .additional
    }

    private func inlineValidationMessages(_ messages: [String], identifier: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(messages, id: \.self) { message in
                Label(message, systemImage: "exclamationmark.circle.fill")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .font(.subheadline.weight(.bold))
        .foregroundStyle(CaregiverUI.red)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(CaregiverUI.red.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(CaregiverUI.red.opacity(0.35), lineWidth: 1)
        }
        .accessibilityIdentifier(identifier)
    }

    private func compactFormError(message: String) -> some View {
        Label(message, systemImage: "exclamationmark.triangle.fill")
            .font(.subheadline.weight(.bold))
            .foregroundStyle(CaregiverUI.red)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(CaregiverUI.red.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(CaregiverUI.red.opacity(0.35), lineWidth: 1)
            }
            .accessibilityIdentifier("MedicationSubmissionError")
    }

    // MARK: - Buttons

    @ViewBuilder
    private func saveButton(
        isCaregiverMissingPatient: Bool,
        scrollProxy: ScrollViewProxy
    ) -> some View {
        Button {
            let validationMessages = viewModel.validate()
            guard validationMessages.isEmpty else {
                viewModel.errorMessage = nil
                showsValidationErrors = true
                let target = firstValidationTarget(for: validationMessages)
                if target == .additional {
                    showsAdditionalSettings = true
                }
                DispatchQueue.main.async {
                    guard let target else { return }
                    withAnimation(.easeInOut(duration: 0.3)) {
                        scrollProxy.scrollTo(target, anchor: .top)
                    }
                }
                return
            }

            showsValidationErrors = false
            viewModel.errorMessage = nil
            Task {
                let saved = await viewModel.submit()
                if saved {
                    if !viewModel.isEditing {
                        AnalyticsService.shared.logCoreActionCompleted(.medicationCreated)
                    }
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
                    Text(viewModel.isEditing
                         ? NSLocalizedString("common.save", comment: "Save")
                         : NSLocalizedString("medication.form.submit.register", comment: "Register medication"))
                }
            }
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(CaregiverUI.teal, in: RoundedRectangle(cornerRadius: 14))
        }
        .disabled(viewModel.isSubmitting || isCaregiverMissingPatient)
        .opacity(isCaregiverMissingPatient ? 0.5 : 1)
        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        .accessibilityLabel(viewModel.isEditing
            ? NSLocalizedString("common.save", comment: "Save")
            : NSLocalizedString("medication.form.submit.register", comment: "Register medication"))
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

private enum MedicationFormScrollTarget: Hashable {
    case basic
    case schedule
    case additional
}
