import SwiftUI

struct InventoryDetailView: View {
    let item: InventoryItemDTO
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) private var dismiss
    private let onSaved: (() -> Void)?

    @State private var inventoryEnabled: Bool
    @State private var quantity: Int
    @State private var lowThreshold: Int
    @State private var refillAmount: Int
    @State private var errorMessage: String?
    @State private var savedEnabled: Bool
    @State private var savedLowThreshold: Int
    @State private var pendingRefillAmount: Int?
    @State private var pendingCorrectionAmount: Int?
    @State private var showRefillConfirm = false
    @State private var showCorrectionConfirm = false
    @State private var toastMessage: String?
    @State private var lastFailedAction: InventoryDetailAction?
    @State private var correctionAmount: Int
    @FocusState private var focusedField: InventoryField?

    private let numberFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .none
        formatter.maximumFractionDigits = 0
        return formatter
    }()

    init(item: InventoryItemDTO, viewModel: InventoryViewModel, onSaved: (() -> Void)? = nil) {
        self.item = item
        self.viewModel = viewModel
        self.onSaved = onSaved
        _inventoryEnabled = State(initialValue: item.inventoryEnabled)
        _quantity = State(initialValue: item.inventoryQuantity)
        _lowThreshold = State(initialValue: item.inventoryLowThreshold)
        _refillAmount = State(initialValue: 0)
        _savedEnabled = State(initialValue: item.inventoryEnabled)
        _savedLowThreshold = State(initialValue: item.inventoryLowThreshold)
        _correctionAmount = State(initialValue: item.inventoryQuantity)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VStack(spacing: 16) {
                    inventoryHeader
                        .padding(.horizontal, 16)

                    Form {
                        Section(
                            header: Text(NSLocalizedString("caregiver.inventory.detail.section.settings", comment: "Settings section")),
                            footer: Text(NSLocalizedString("caregiver.inventory.settings.note", comment: "Low stock note"))
                                .font(.footnote)
                                .foregroundColor(.secondary)
                        ) {
                            Toggle(NSLocalizedString("caregiver.inventory.detail.enabled", comment: "Inventory enabled"), isOn: $inventoryEnabled)
                            HStack {
                                Text(NSLocalizedString("caregiver.inventory.detail.threshold", comment: "Threshold label"))
                                Spacer()
                                TextField("0", value: $lowThreshold, formatter: numberFormatter)
                                    .multilineTextAlignment(.trailing)
                                    .keyboardType(.numberPad)
                                    .focused($focusedField, equals: .lowThreshold)
                                    .accessibilityIdentifier("InventoryThresholdField")
                            }
                        }

                        Section(header: Text(NSLocalizedString("caregiver.inventory.detail.section.adjust", comment: "Adjust section"))) {
                            HStack {
                                Text(NSLocalizedString("caregiver.inventory.detail.refill", comment: "Refill label"))
                                Spacer()
                                TextField("0", value: $refillAmount, formatter: numberFormatter)
                                    .multilineTextAlignment(.trailing)
                                    .keyboardType(.numberPad)
                            }
                            Button(NSLocalizedString("caregiver.inventory.actions.refill.sheet.confirm", comment: "Confirm refill")) {
                                let amount = max(0, refillAmount)
                                guard amount > 0 else { return }
                                pendingRefillAmount = amount
                                showRefillConfirm = true
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(!inventoryEnabled)

                            HStack {
                                Text(NSLocalizedString("caregiver.inventory.detail.set", comment: "Set label"))
                                Spacer()
                                TextField("0", value: $correctionAmount, formatter: numberFormatter)
                                    .multilineTextAlignment(.trailing)
                                    .keyboardType(.numberPad)
                            }
                            Button(NSLocalizedString("caregiver.inventory.actions.correction.button", comment: "Correction action")) {
                                let amount = max(0, correctionAmount)
                                pendingCorrectionAmount = amount
                                showCorrectionConfirm = true
                            }
                            .buttonStyle(.bordered)
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
        .confirmationDialog(
            NSLocalizedString("caregiver.inventory.correction.title", comment: "Correction title"),
            isPresented: $showCorrectionConfirm,
            presenting: pendingCorrectionAmount
        ) { amount in
            Button(NSLocalizedString("caregiver.inventory.actions.correction.button", comment: "Correction action")) {
                Task { await applyCorrection(amount: amount) }
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
        } message: { amount in
            Text(
                String(
                    format: NSLocalizedString(
                        "caregiver.inventory.correction.message",
                        comment: "Correction confirm message"
                    ),
                    amount
                )
            )
        }
        .overlay(alignment: .top) {
            if let toastMessage {
                Text(toastMessage)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                    .shadow(radius: 4)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .accessibilityLabel(toastMessage)
            }
        }
        .sensoryFeedback(.success, trigger: toastMessage)
    }

    private var inventoryHeader: some View {
        VStack(alignment: .leading, spacing: 8) {
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
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
        let sanitizedThreshold = max(0, lowThreshold)
        if quantity == 0 {
            return .out
        }
        if quantity < sanitizedThreshold {
            return .low
        }
        return .available
    }

    private var hasSettingsChanges: Bool {
        let sanitizedThreshold = max(0, lowThreshold)
        return inventoryEnabled != savedEnabled || sanitizedThreshold != savedLowThreshold
    }

    private func saveSettings() async {
        errorMessage = nil
        let sanitizedThreshold = max(0, lowThreshold)
        let updated = await viewModel.updateSettings(
            item: item,
            enabled: inventoryEnabled,
            quantity: nil,
            threshold: sanitizedThreshold
        )
        if let updated {
            quantity = updated.inventoryQuantity
            lowThreshold = updated.inventoryLowThreshold
            savedEnabled = updated.inventoryEnabled
            savedLowThreshold = updated.inventoryLowThreshold
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
            showToast(NSLocalizedString("common.toast.updated", comment: "Updated toast"))
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            lastFailedAction = .refill(amount)
        }
    }

    private func applyCorrection(amount: Int) async {
        errorMessage = nil
        let updated = await viewModel.adjustInventory(
            item: item,
            reason: "SET",
            delta: nil,
            absoluteQuantity: amount
        )
        if let updated {
            quantity = updated.inventoryQuantity
            correctionAmount = updated.inventoryQuantity
            showToast(NSLocalizedString("common.toast.updated", comment: "Updated toast"))
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            lastFailedAction = .correction(amount)
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
        case let .correction(amount):
            await applyCorrection(amount: amount)
        }
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task {
            try? await Task.sleep(for: .seconds(1))
            await MainActor.run {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
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
    case correction(Int)
}

private enum InventoryField {
    case lowThreshold
}
