import SwiftUI

struct InventoryDetailView: View {
    let item: InventoryItemDTO
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var inventoryEnabled: Bool
    @State private var quantity: Int
    @State private var lowThreshold: Int
    @State private var refillAmount: Int
    @State private var setAmount: Int
    @State private var errorMessage: String?

    private let numberFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .none
        formatter.maximumFractionDigits = 0
        return formatter
    }()

    init(item: InventoryItemDTO, viewModel: InventoryViewModel) {
        self.item = item
        self.viewModel = viewModel
        _inventoryEnabled = State(initialValue: item.inventoryEnabled)
        _quantity = State(initialValue: item.inventoryQuantity)
        _lowThreshold = State(initialValue: item.inventoryLowThreshold)
        _refillAmount = State(initialValue: 0)
        _setAmount = State(initialValue: item.inventoryQuantity)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Form {
                    Section(header: Text(NSLocalizedString("caregiver.inventory.detail.section.settings", comment: "Settings section"))) {
                        Toggle(NSLocalizedString("caregiver.inventory.detail.enabled", comment: "Inventory enabled"), isOn: $inventoryEnabled)
                        HStack {
                            Text(NSLocalizedString("caregiver.inventory.detail.quantity", comment: "Quantity label"))
                            Spacer()
                            TextField("", value: $quantity, formatter: numberFormatter)
                                .multilineTextAlignment(.trailing)
                                .keyboardType(.numberPad)
                                .accessibilityIdentifier("InventoryQuantityField")
                        }
                        HStack {
                            Text(NSLocalizedString("caregiver.inventory.detail.threshold", comment: "Threshold label"))
                            Spacer()
                            TextField("", value: $lowThreshold, formatter: numberFormatter)
                                .multilineTextAlignment(.trailing)
                                .keyboardType(.numberPad)
                                .accessibilityIdentifier("InventoryThresholdField")
                        }
                        Button(NSLocalizedString("common.save", comment: "Save")) {
                            Task { await saveSettings() }
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    Section(header: Text(NSLocalizedString("caregiver.inventory.detail.section.adjust", comment: "Adjust section"))) {
                        HStack {
                            Text(NSLocalizedString("caregiver.inventory.detail.refill", comment: "Refill label"))
                            Spacer()
                            TextField("", value: $refillAmount, formatter: numberFormatter)
                                .multilineTextAlignment(.trailing)
                                .keyboardType(.numberPad)
                        }
                        Button(NSLocalizedString("caregiver.inventory.detail.refill.action", comment: "Refill action")) {
                            Task { await applyRefill() }
                        }
                        .buttonStyle(.bordered)

                        HStack {
                            Text(NSLocalizedString("caregiver.inventory.detail.set", comment: "Set label"))
                            Spacer()
                            TextField("", value: $setAmount, formatter: numberFormatter)
                                .multilineTextAlignment(.trailing)
                                .keyboardType(.numberPad)
                        }
                        Button(NSLocalizedString("caregiver.inventory.detail.set.action", comment: "Set action")) {
                            Task { await applySet() }
                        }
                        .buttonStyle(.bordered)
                    }

                    if let errorMessage {
                        Section {
                            ErrorStateView(message: errorMessage)
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
        }
    }

    private func saveSettings() async {
        errorMessage = nil
        let sanitizedQuantity = max(0, quantity)
        let sanitizedThreshold = max(0, lowThreshold)
        let updated = await viewModel.updateSettings(
            item: item,
            enabled: inventoryEnabled,
            quantity: sanitizedQuantity,
            threshold: sanitizedThreshold
        )
        if let updated {
            quantity = updated.inventoryQuantity
            lowThreshold = updated.inventoryLowThreshold
            dismiss()
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
        }
    }

    private func applyRefill() async {
        errorMessage = nil
        let amount = max(0, refillAmount)
        guard amount > 0 else { return }
        let updated = await viewModel.adjustInventory(
            item: item,
            reason: "REFILL",
            delta: amount,
            absoluteQuantity: nil
        )
        if let updated {
            quantity = updated.inventoryQuantity
            refillAmount = 0
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
        }
    }

    private func applySet() async {
        errorMessage = nil
        let amount = max(0, setAmount)
        let updated = await viewModel.adjustInventory(
            item: item,
            reason: "SET",
            delta: nil,
            absoluteQuantity: amount
        )
        if let updated {
            quantity = updated.inventoryQuantity
            setAmount = updated.inventoryQuantity
        } else {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
        }
    }
}
