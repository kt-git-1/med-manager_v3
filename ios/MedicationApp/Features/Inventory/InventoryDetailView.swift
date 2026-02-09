import SwiftUI

struct InventoryDetailView: View {
    let item: InventoryItemDTO
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) private var dismiss
    private let onSaved: (() -> Void)?
    private let onRefilled: (() -> Void)?

    @State private var inventoryEnabled: Bool
    @State private var quantity: Int
    @State private var refillAmount: Int
    @State private var errorMessage: String?
    @State private var savedEnabled: Bool
    @State private var pendingRefillAmount: Int?
    @State private var showRefillConfirm = false
    @State private var lastFailedAction: InventoryDetailAction?
    @FocusState private var focusedField: InventoryField?

    private let numberFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .none
        formatter.maximumFractionDigits = 0
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
        _savedEnabled = State(initialValue: item.inventoryEnabled)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VStack(spacing: 16) {
                    inventoryHeader
                        .padding(.horizontal, 16)

                    Form {
                        Section(
                            header: Text(NSLocalizedString("caregiver.inventory.detail.section.settings", comment: "Settings section"))
                        ) {
                            Toggle(NSLocalizedString("caregiver.inventory.detail.enabled", comment: "Inventory enabled"), isOn: $inventoryEnabled)
                        }

                        Section(header: Text(NSLocalizedString("caregiver.inventory.detail.section.adjust", comment: "Adjust section"))) {
                            HStack(spacing: 10) {
                                refillPresetButton(title: "+7", amount: 7)
                                refillPresetButton(title: "+14", amount: 14)
                                refillPresetButton(title: "+21", amount: 21)
                                Button(NSLocalizedString("caregiver.inventory.actions.refill.sheet.custom", comment: "Custom input")) {
                                    refillAmount = max(0, refillAmount)
                                    focusedField = .refillAmount
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                            }
                            HStack {
                                Text(NSLocalizedString("caregiver.inventory.detail.refill", comment: "Refill label"))
                                Spacer()
                                TextField("0", value: $refillAmount, formatter: numberFormatter)
                                    .multilineTextAlignment(.trailing)
                                    .keyboardType(.numberPad)
                                    .focused($focusedField, equals: .refillAmount)
                            }
                            Button {
                                let amount = max(0, refillAmount)
                                guard amount > 0 else { return }
                                pendingRefillAmount = amount
                                showRefillConfirm = true
                            } label: {
                                Label(
                                    NSLocalizedString("caregiver.inventory.actions.refill.sheet.confirm", comment: "Confirm refill"),
                                    systemImage: "plus.circle.fill"
                                )
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 12))
                            }
                            .disabled(!inventoryEnabled)
                            .opacity(inventoryEnabled ? 1 : 0.5)
                            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                        }

                        if let errorMessage {
                            Section {
                                ErrorStateView(message: errorMessage)
                                if lastFailedAction != nil {
                                    Button(NSLocalizedString("common.retry", comment: "Retry")) {
                                        Task { await retryLastAction() }
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                }

                if viewModel.isUpdating {
                    ZStack {
                        Color.black.opacity(0.2)
                            .ignoresSafeArea()
                        LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                            .padding(16)
                            .glassEffect(.regular, in: .rect(cornerRadius: 16))
                    }
                    .accessibilityIdentifier("InventoryUpdatingOverlay")
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
                    amount,
                    quantity,
                    quantity + amount
                )
            )
        }
    }

    private var inventoryHeader: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "archivebox.circle.fill")
                    .font(.system(size: 36))
                    .foregroundStyle(.tint)
                    .symbolRenderingMode(.hierarchical)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.title3.weight(.bold))
                    Text(dailyIntakeSummaryText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                statusBadge
            }

            Divider()

            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(NSLocalizedString("caregiver.inventory.remaining.label", comment: "Remaining label"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text(inventoryEnabled ? "\(quantity)" : "—")
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(inventoryEnabled ? Color.primary : Color.secondary)
                Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                    .font(.headline)
                    .foregroundStyle(.secondary)
            }

            refillPlanSummary
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
        .overlay {
            if shouldHighlightLowStock {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.red.opacity(0.7), lineWidth: 2)
            }
        }
    }

    private var refillPlanSummary: some View {
        VStack(alignment: .leading, spacing: 6) {
            if item.isPrn {
                Text(NSLocalizedString("medication.list.badge.prn", comment: "PRN badge"))
                    .font(.headline)
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
            .foregroundStyle(inventoryStatus.color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(inventoryStatus.color.opacity(0.15), in: Capsule())
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
            count,
            item.doseCountPerIntake
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

    private func applyRefill(amount: Int) async {
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

    private func retryLastAction() async {
        guard let lastFailedAction else { return }
        self.lastFailedAction = nil
        switch lastFailedAction {
        case .saveSettings:
            await saveSettings()
        case let .refill(amount):
            await applyRefill(amount: amount)
        }
    }

    private func refillPresetButton(title: String, amount: Int) -> some View {
        Button {
            refillAmount = amount
        } label: {
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(refillAmount == amount ? Color.white : Color.accentColor)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    refillAmount == amount
                        ? Color.accentColor
                        : Color.accentColor.opacity(0.12),
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
    case refill(Int)
}

private enum InventoryField {
    case refillAmount
}
