import SwiftUI

struct InventoryDetailView: View {
    let item: InventoryItemDTO
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) private var dismiss
    private let onSaved: (() -> Void)?
    private let onRefilled: (() -> Void)?

    @State private var inventoryEnabled: Bool
    @State private var quantity: Double
    @State private var refillAmount: Double
    @State private var errorMessage: String?
    @State private var savedEnabled: Bool
    @State private var correctionQuantity: Double = 0
    @State private var showCorrectionConfirm = false
    @State private var pendingRefillAmount: Double?
    @State private var showRefillConfirm = false
    @State private var lastFailedAction: InventoryDetailAction?
    @FocusState private var focusedField: InventoryField?

    private let numberFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 1
        formatter.minimumFractionDigits = 0
        return formatter
    }()

    init(
        item: InventoryItemDTO,
        viewModel: InventoryViewModel,
        onSaved: (() -> Void)? = nil,
        onRefilled: (() -> Void)? = nil
    ) {
        self.item = item
        self.viewModel = viewModel
        self.onSaved = onSaved
        self.onRefilled = onRefilled
        _inventoryEnabled = State(initialValue: item.inventoryEnabled)
        _quantity = State(initialValue: item.inventoryQuantity)
        _refillAmount = State(initialValue: 0)
        _correctionQuantity = State(initialValue: item.inventoryQuantity)
        _savedEnabled = State(initialValue: item.inventoryEnabled)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                CaregiverScreenBackground {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 18) {
                            inventoryHeader
                            settingsCard
                            refillCard
                            correctionCard
                            errorCard
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                        .padding(.bottom, 32)
                    }
                }

                if viewModel.isUpdating {
                    SchedulingRefreshOverlay()
                }
            }
            .navigationTitle(NSLocalizedString("caregiver.tabs.inventory", comment: "Inventory tab"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(NSLocalizedString("common.save", comment: "Save")) {
                        Task { await saveSettings() }
                    }
                    .disabled(!hasSettingsChanges)
                }
            }
        }
        .confirmationDialog(
            NSLocalizedString("caregiver.inventory.refill.confirm.title", comment: "Refill confirm title"),
            isPresented: $showRefillConfirm,
            presenting: pendingRefillAmount
        ) { amount in
            Button(NSLocalizedString("caregiver.inventory.detail.refill.action", comment: "Refill action")) {
                Task { await applyRefill(amount: amount) }
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
        } message: { amount in
            Text(
                String(
                    format: NSLocalizedString(
                        "caregiver.inventory.refill.confirm.message",
                        comment: "Refill confirm message"
                    ),
                    item.name,
                    AppConstants.formatDecimal(amount),
                    AppConstants.formatDecimal(quantity),
                    AppConstants.formatDecimal(quantity + amount)
                )
            )
        }
        .alert(
            NSLocalizedString("caregiver.inventory.correction.title", comment: "Correction confirm title"),
            isPresented: $showCorrectionConfirm
        ) {
            Button(NSLocalizedString("caregiver.inventory.actions.correction.button", comment: "Correction action")) {
                Task { await applyCorrection() }
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
        } message: {
            Text(
                String(
                    format: NSLocalizedString(
                        "caregiver.inventory.correction.message",
                        comment: "Correction confirm message"
                    ),
                    AppConstants.formatDecimal(max(0, correctionQuantity))
                )
            )
        }
    }

    private var inventoryHeader: some View {
        CaregiverCard(accent: shouldHighlightLowStock ? CaregiverUI.red : CaregiverUI.teal) {
            VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Image(systemName: "shippingbox.fill")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(inventoryStatus.color, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.title.weight(.bold))
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(dailyIntakeSummaryText)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
                Spacer(minLength: 0)
                statusBadge
            }

            Divider()

            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(NSLocalizedString("caregiver.inventory.remaining.label", comment: "Remaining label"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text(inventoryEnabled ? AppConstants.formatDecimal(quantity) : "—")
                    .font(.system(size: 52, weight: .bold, design: .rounded))
                    .foregroundStyle(inventoryEnabled ? inventoryStatus.color : Color.secondary)
                Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                    .font(.title3.weight(.bold))
                    .foregroundStyle(inventoryStatus.color)
            }

            refillPlanSummary
        }
        }
    }

    private var settingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(NSLocalizedString("caregiver.inventory.detail.section.settings", comment: "Settings section"))
            CaregiverCard {
                Toggle(NSLocalizedString("caregiver.inventory.detail.enabled", comment: "Inventory enabled"), isOn: $inventoryEnabled)
                    .font(.title3.weight(.semibold))
                    .tint(CaregiverUI.teal)
                    .padding(.vertical, 4)
            }
        }
    }

    private var refillCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(NSLocalizedString("caregiver.inventory.detail.section.adjust", comment: "Adjust section"))
            CaregiverCard {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 10) {
                        refillPresetButton(title: "+7", amount: 7)
                        refillPresetButton(title: "+14", amount: 14)
                        refillPresetButton(title: "+21", amount: 21)
                        Button(NSLocalizedString("caregiver.inventory.actions.refill.sheet.custom", comment: "Custom input")) {
                            refillAmount = max(0, refillAmount)
                            focusedField = .refillAmount
                        }
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(CaregiverUI.blue)
                        .padding(.horizontal, 14)
                        .frame(height: 38)
                        .background(Color(.secondarySystemGroupedBackground), in: Capsule())
                        .buttonStyle(.plain)
                    }

                    Divider()

                    quantityInputRow(
                        title: NSLocalizedString("caregiver.inventory.detail.refill", comment: "Refill label"),
                        value: $refillAmount,
                        field: .refillAmount,
                        showUnit: false
                    )

                    CaregiverPrimaryButton(
                        title: NSLocalizedString("caregiver.inventory.actions.refill.sheet.confirm", comment: "Confirm refill"),
                        systemImage: "plus.circle.fill",
                        color: inventoryEnabled ? CaregiverUI.teal : .gray
                    ) {
                        let amount = max(0, refillAmount)
                        guard amount > 0 else { return }
                        pendingRefillAmount = amount
                        showRefillConfirm = true
                    }
                    .disabled(!inventoryEnabled)
                    .opacity(inventoryEnabled ? 1 : 0.55)
                }
            }
        }
    }

    private var correctionCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(NSLocalizedString("caregiver.inventory.actions.correction", comment: "Correction section"))
            CaregiverCard {
                VStack(alignment: .leading, spacing: 16) {
                    quantityInputRow(
                        title: NSLocalizedString("caregiver.inventory.detail.set", comment: "Correction quantity label"),
                        value: $correctionQuantity,
                        field: .correctionQuantity,
                        showUnit: true
                    )

                    Divider()

                    CaregiverPrimaryButton(
                        title: NSLocalizedString("caregiver.inventory.actions.correction.button", comment: "Correction button"),
                        systemImage: "pencil.circle.fill",
                        color: inventoryEnabled ? CaregiverUI.orange : .gray
                    ) {
                        showCorrectionConfirm = true
                    }
                    .disabled(!inventoryEnabled)
                    .opacity(inventoryEnabled ? 1 : 0.55)
                }
            }
        }
    }

    @ViewBuilder
    private var errorCard: some View {
        if let errorMessage {
            CaregiverCard(accent: CaregiverUI.red) {
                VStack(spacing: 12) {
                    ErrorStateView(message: errorMessage)
                    if lastFailedAction != nil {
                        Button(NSLocalizedString("common.retry", comment: "Retry")) {
                            Task { await retryLastAction() }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.title3.weight(.bold))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 2)
    }

    private func quantityInputRow(
        title: String,
        value: Binding<Double>,
        field: InventoryField,
        showUnit: Bool
    ) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(title)
                .font(.title3.weight(.semibold))
            Spacer(minLength: 10)
            TextField("0", value: value, formatter: numberFormatter)
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.trailing)
                .keyboardType(.decimalPad)
                .focused($focusedField, equals: field)
                .frame(width: 92)
            if showUnit {
                Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private var refillPlanSummary: some View {
        VStack(alignment: .leading, spacing: 6) {
            if item.isPrn {
                Text(NSLocalizedString("medication.list.badge.prn", comment: "PRN badge"))
                    .font(.headline)
                Text(String(
                    format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"),
                    AppConstants.formatDecimal(item.doseCountPerIntake)
                ))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            } else {
                Text(refillDaysText)
                    .font(.headline)
                HStack(spacing: 8) {
                    Text(NSLocalizedString("caregiver.inventory.plan.refillDue", comment: "Refill due label"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(refillDueDateText)
                        .font(.subheadline.weight(.semibold))
                }
            }
        }
    }

    private var statusBadge: some View {
            Text(inventoryStatus.title)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(inventoryStatus.color, in: Capsule())
    }

    private var inventoryStatus: InventoryStatus {
        if !inventoryEnabled {
            return .unconfigured
        }
        let sanitizedThreshold = max(0, item.inventoryLowThreshold)
        if let daysRemaining = item.daysRemaining {
            if daysRemaining <= 0 {
                return .out
            }
        } else if quantity <= 0 {
            return .out
        }
        if sanitizedThreshold > 0, let daysRemaining = item.daysRemaining, daysRemaining <= sanitizedThreshold {
            return .low
        }
        return .available
    }

    private var shouldHighlightLowStock: Bool {
        inventoryStatus == .low || inventoryStatus == .out
    }

    private var refillDaysText: String {
        if item.isPrn {
            return NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
        }
        guard let daysRemaining = item.daysRemaining else {
            return "—"
        }
        return String(
            format: NSLocalizedString(
                "caregiver.inventory.plan.daysRemaining",
                comment: "Remaining days label"
            ),
            daysRemaining
        )
    }

    private var refillDueDateText: String {
        item.refillDueDate ?? "—"
    }

    private var dailyIntakeSummaryText: String {
        if item.isPrn {
            return NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
        }
        guard let dailyPlannedUnits = item.dailyPlannedUnits, item.doseCountPerIntake > 0 else {
            return NSLocalizedString("caregiver.inventory.dailyIntake.unknown", comment: "Unknown daily intake")
        }
        let count = dailyPlannedUnits / item.doseCountPerIntake
        return String(
            format: NSLocalizedString("caregiver.inventory.dailyIntake.format", comment: "Daily intake format"),
            AppConstants.formatDecimal(count),
            AppConstants.formatDecimal(item.doseCountPerIntake)
        )
    }

    private var hasSettingsChanges: Bool {
        return inventoryEnabled != savedEnabled
    }

    private func saveSettings() async {
        errorMessage = nil
        let updated = await viewModel.updateSettings(
            item: item,
            enabled: inventoryEnabled,
            quantity: nil
        )
        if let updated {
            quantity = updated.inventoryQuantity
            savedEnabled = updated.inventoryEnabled
            onSaved?()
            dismiss()
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            lastFailedAction = .saveSettings
        }
    }

    private func applyRefill(amount: Double) async {
        errorMessage = nil
        let updated = await viewModel.adjustInventory(
            item: item,
            reason: "REFILL",
            delta: amount,
            absoluteQuantity: nil
        )
        if let updated {
            quantity = updated.inventoryQuantity
            refillAmount = 0
            onRefilled?()
            dismiss()
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            lastFailedAction = .refill(amount)
        }
    }

    private func applyCorrection() async {
        errorMessage = nil
        let newQuantity = max(0, correctionQuantity)
        let updated = await viewModel.adjustInventory(
            item: item,
            reason: "SET",
            delta: nil,
            absoluteQuantity: newQuantity
        )
        if let updated {
            quantity = updated.inventoryQuantity
            onSaved?()
            dismiss()
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            lastFailedAction = .correction(newQuantity)
        }
    }

    private func retryLastAction() async {
        guard let lastFailedAction else { return }
        self.lastFailedAction = nil
        switch lastFailedAction {
        case .saveSettings:
            await saveSettings()
        case let .refill(amount):
            await applyRefill(amount: amount)
        case .correction:
            await applyCorrection()
        }
    }

    private func refillPresetButton(title: String, amount: Double) -> some View {
        Button {
            refillAmount = amount
        } label: {
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(refillAmount == amount ? Color.white : CaregiverUI.blue)
                .padding(.horizontal, 14)
                .frame(height: 38)
                .background(
                    refillAmount == amount
                        ? CaregiverUI.blue
                        : CaregiverUI.blue.opacity(0.12),
                    in: Capsule()
                )
        }
        .buttonStyle(.plain)
    }

}

private enum InventoryStatus {
    case available
    case low
    case out
    case unconfigured

    var title: String {
        switch self {
        case .available:
            return NSLocalizedString("caregiver.inventory.status.available", comment: "Available status")
        case .low:
            return NSLocalizedString("caregiver.inventory.status.low", comment: "Low status")
        case .out:
            return NSLocalizedString("caregiver.inventory.status.out", comment: "Out status")
        case .unconfigured:
            return NSLocalizedString("caregiver.inventory.status.unconfigured", comment: "Unconfigured status")
        }
    }

    var color: Color {
        switch self {
        case .available:
            return .green
        case .low:
            return .orange
        case .out:
            return .red
        case .unconfigured:
            return .gray
        }
    }
}

private enum InventoryDetailAction {
    case saveSettings
    case refill(Double)
    case correction(Double)
}

private enum InventoryField {
    case refillAmount
    case correctionQuantity
}
