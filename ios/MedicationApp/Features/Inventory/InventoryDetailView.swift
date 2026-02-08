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
                            HStack(spacing: 12) {
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
                            Button(NSLocalizedString("caregiver.inventory.actions.refill.sheet.confirm", comment: "Confirm refill")) {
                                let amount = max(0, refillAmount)
                                guard amount > 0 else { return }
                                pendingRefillAmount = amount
                                showRefillConfirm = true
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(!inventoryEnabled)
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
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .shadow(radius: 6)
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
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("薬名")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.secondary)
                Text(item.name)
                    .font(.headline)
            }
            Text(NSLocalizedString("caregiver.inventory.remaining.label", comment: "Remaining label"))
                .font(.subheadline.weight(.semibold))
                .foregroundColor(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(inventoryEnabled ? "\(quantity)" : "—")
                    .font(.largeTitle.weight(.bold))
                    .foregroundColor(inventoryEnabled ? .primary : .secondary)
                Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                    .font(.headline)
                    .foregroundColor(.secondary)
                Spacer()
                statusBadge
            }
            Text(dailyIntakeSummaryText)
                .font(.subheadline.weight(.semibold))
            refillPlanSummary
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .overlay {
            if shouldHighlightLowStock {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.red.opacity(0.7), lineWidth: 2)
                    .shadow(color: Color.red.opacity(0.3), radius: 8)
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
                        .foregroundColor(.secondary)
                    Text(refillDueDateText)
                        .font(.subheadline.weight(.semibold))
                }
            }
        }
    }

    private var statusBadge: some View {
        Text(inventoryStatus.title)
            .font(.caption.weight(.bold))
            .foregroundColor(inventoryStatus.color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(inventoryStatus.color.opacity(0.15), in: Capsule())
    }

    private var inventoryStatus: InventoryStatus {
        if !inventoryEnabled {
            return .unconfigured
        }
        let sanitizedThreshold = max(0, item.inventoryLowThreshold)
        if quantity == 0 {
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
            return "1日—回（—錠/回）"
        }
        let count = dailyPlannedUnits / item.doseCountPerIntake
        return "1日\(count)回（\(item.doseCountPerIntake)錠ずつ）"
    }

    private var hasSettingsChanges: Bool {
        return inventoryEnabled != savedEnabled
    }

    private func saveSettings() async {
        errorMessage = nil
        let updated = await viewModel.updateSettings(
            item: item,
            enabled: inventoryEnabled,
            quantity: nil,
            threshold: item.inventoryLowThreshold
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
        Button(title) {
            refillAmount = amount
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
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
