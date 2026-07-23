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
    @State private var selectedAction: InventoryEditAction = .refill
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
        let suggestedRefill = item.nextFourteenDaysPlannedUnits
            ?? item.dailyPlannedUnits.map { $0 * 14 }
            ?? max(item.doseCountPerIntake, 1) * 14
        _refillAmount = State(initialValue: max(0, suggestedRefill))
        _correctionQuantity = State(initialValue: item.inventoryQuantity)
        _savedEnabled = State(initialValue: item.inventoryEnabled)
    }

    var body: some View {
        ZStack {
            CaregiverScreenBackground {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        inventoryHeader
                        actionPicker
                        if selectedAction == .refill {
                            refillEditor
                        } else {
                            correctionEditor
                        }
                        inventorySettings
                        errorCard
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, 34)
                }
                .scrollDismissesKeyboard(.interactively)
            }

            if viewModel.isUpdating {
                SchedulingRefreshOverlay()
            }
        }
        .navigationTitle(NSLocalizedString("caregiver.inventory.edit.title", comment: "Edit inventory title"))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(CaregiverUI.teal)
                }
                .accessibilityLabel(NSLocalizedString("common.back", comment: "Back"))
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                if hasSettingsChanges {
                    Button(NSLocalizedString("common.save", comment: "Save")) {
                        Task { await saveSettings() }
                    }
                    .fontWeight(.bold)
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
        HStack(alignment: .center, spacing: 12) {
                InventoryIllustrationView(tint: inventoryStatus.color, isPrn: item.isPrn)
                    .frame(width: 58, height: 58)

                VStack(alignment: .leading, spacing: 8) {
                    Text(item.name)
                        .font(.title3.weight(.bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)

                    HStack(spacing: 10) {
                        inventoryMetric(
                            label: NSLocalizedString("caregiver.inventory.edit.current", comment: "Current inventory"),
                            value: inventoryEnabled ? AppConstants.formatDecimal(quantity) : "—",
                            suffix: NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit")
                        )
                        Divider().frame(height: 34)
                        inventoryMetric(
                            label: NSLocalizedString("caregiver.inventory.edit.daysRemaining", comment: "Days remaining"),
                            value: item.daysRemaining.map(String.init) ?? "—",
                            suffix: NSLocalizedString("common.days.unit", comment: "Days unit")
                        )
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke((shouldHighlightLowStock ? CaregiverUI.orange : CaregiverUI.teal).opacity(0.55), lineWidth: 1.5)
        }
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
        .accessibilityIdentifier("InventoryEditHeader")
    }

    private func inventoryMetric(label: String, value: String, suffix: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 25, weight: .bold, design: .rounded))
                    .foregroundStyle(CaregiverUI.tealDark)
                Text(suffix)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var actionPicker: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(NSLocalizedString("caregiver.inventory.edit.question", comment: "Inventory action question"))
                .font(.title2.weight(.bold))
                .padding(.horizontal, 2)

            inventoryActionButton(
                action: .refill,
                title: NSLocalizedString("caregiver.inventory.edit.refill.title", comment: "Refill action title"),
                message: NSLocalizedString("caregiver.inventory.edit.refill.message", comment: "Refill action message"),
                systemImage: "shippingbox.fill",
                tint: CaregiverUI.teal
            )
            inventoryActionButton(
                action: .correction,
                title: NSLocalizedString("caregiver.inventory.edit.correction.title", comment: "Correction action title"),
                message: NSLocalizedString("caregiver.inventory.edit.correction.message", comment: "Correction action message"),
                systemImage: "list.clipboard.fill",
                tint: CaregiverUI.orange
            )
        }
    }

    private func inventoryActionButton(
        action: InventoryEditAction,
        title: String,
        message: String,
        systemImage: String,
        tint: Color
    ) -> some View {
        let isSelected = selectedAction == action
        return Button {
            focusedField = nil
            withAnimation(.snappy) {
                selectedAction = action
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 25, weight: .bold))
                    .foregroundStyle(tint)
                    .frame(width: 46, height: 46)
                    .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 13, style: .continuous))

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(isSelected ? tint : .primary)
                    Text(message)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.76)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Image(systemName: isSelected ? "checkmark.circle.fill" : "chevron.right")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(isSelected ? tint : .secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(isSelected ? tint : CaregiverUI.cardStroke, lineWidth: isSelected ? 2 : 1)
            }
            .shadow(color: CaregiverUI.cardShadow, radius: 6, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(action == .refill ? "InventoryEditActionRefill" : "InventoryEditActionCorrection")
    }

    private var refillEditor: some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(NSLocalizedString("caregiver.inventory.edit.refill.amount", comment: "Refill amount title"))
                        .font(.title2.weight(.bold))
                        .foregroundStyle(CaregiverUI.tealDark)

                    Spacer(minLength: 4)

                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(NSLocalizedString("caregiver.inventory.edit.currentStock", comment: "Current stock"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                        Text("\(AppConstants.formatDecimal(max(0, quantity)))\(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(CaregiverUI.tealDark)
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(CaregiverUI.teal.opacity(0.08), in: Capsule())
                }

                HStack(spacing: 8) {
                    refillPresetButton(title: "7日分", amount: plannedRefillAmount(days: 7))
                    refillPresetButton(title: "14日分", amount: plannedRefillAmount(days: 14))
                    refillPresetButton(title: "21日分", amount: plannedRefillAmount(days: 21))
                    Button(NSLocalizedString("caregiver.inventory.actions.refill.sheet.custom", comment: "Custom input")) {
                        refillAmount = max(0, refillAmount)
                        focusedField = .refillAmount
                    }
                    .font(.caption.weight(.bold))
                    .foregroundStyle(CaregiverUI.tealDark)
                    .frame(maxWidth: .infinity)
                    .frame(height: 42)
                    .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                    .buttonStyle(.plain)
                }

                Divider()

                HStack(spacing: 12) {
                    amountInput(
                        label: NSLocalizedString("caregiver.inventory.edit.refill.thisTime", comment: "This refill"),
                        value: $refillAmount,
                        field: .refillAmount,
                        tint: CaregiverUI.teal
                    )

                    Image(systemName: "arrow.right")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.secondary)

                    resultQuantity(
                        label: NSLocalizedString("caregiver.inventory.edit.afterRefill", comment: "After refill"),
                        value: quantity + max(0, refillAmount),
                        tint: CaregiverUI.teal
                    )
                }

                CaregiverPrimaryButton(
                    title: NSLocalizedString("caregiver.inventory.edit.refill.confirm", comment: "Confirm refill"),
                    systemImage: "shippingbox.fill",
                    color: canRefill ? CaregiverUI.teal : .gray
                ) {
                    let amount = max(0, refillAmount)
                    guard amount > 0 else { return }
                    pendingRefillAmount = amount
                    showRefillConfirm = true
                }
                .disabled(!canRefill)
                .opacity(canRefill ? 1 : 0.55)
            }
        }
        .accessibilityIdentifier("InventoryRefillEditor")
    }

    private var correctionEditor: some View {
        CaregiverCard(accent: CaregiverUI.orange) {
            VStack(alignment: .leading, spacing: 16) {
                Text(NSLocalizedString("caregiver.inventory.edit.correction.amount", comment: "Correction amount title"))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(CaregiverUI.orange)
                Text(NSLocalizedString("caregiver.inventory.edit.correction.help", comment: "Correction help"))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)

                HStack(spacing: 12) {
                    resultQuantity(
                        label: NSLocalizedString("caregiver.inventory.edit.beforeCorrection", comment: "Before correction"),
                        value: quantity,
                        tint: .secondary
                    )
                    Image(systemName: "arrow.right")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.secondary)
                    amountInput(
                        label: NSLocalizedString("caregiver.inventory.edit.afterCorrection", comment: "After correction"),
                        value: $correctionQuantity,
                        field: .correctionQuantity,
                        tint: CaregiverUI.orange
                    )
                }

                CaregiverPrimaryButton(
                    title: NSLocalizedString("caregiver.inventory.edit.correction.confirm", comment: "Confirm correction"),
                    systemImage: "pencil.circle.fill",
                    color: inventoryEnabled ? CaregiverUI.orange : .gray
                ) {
                    showCorrectionConfirm = true
                }
                .disabled(!inventoryEnabled)
                .opacity(inventoryEnabled ? 1 : 0.55)
            }
        }
        .accessibilityIdentifier("InventoryCorrectionEditor")
    }

    private func amountInput(
        label: String,
        value: Binding<Double>,
        field: InventoryField,
        tint: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(tint)

            HStack(alignment: .firstTextBaseline, spacing: 4) {
                TextField("0", value: value, formatter: numberFormatter)
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(tint)
                    .multilineTextAlignment(.trailing)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: field)
                    .frame(minWidth: 70)
                Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                    .font(.subheadline.weight(.bold))
            }
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity)
        .frame(height: 76)
        .background(tint.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(tint.opacity(0.5), lineWidth: 1.4)
        }
    }

    private func resultQuantity(label: String, value: Double, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(AppConstants.formatDecimal(max(0, value)))
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(tint)
                Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                    .font(.subheadline.weight(.bold))
            }
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 76)
        .background(tint.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var inventorySettings: some View {
        CaregiverCard {
            Toggle(NSLocalizedString("caregiver.inventory.detail.enabled", comment: "Inventory enabled"), isOn: $inventoryEnabled)
                .font(.headline.weight(.semibold))
                .tint(CaregiverUI.teal)
                .padding(.vertical, 2)
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

    private var canRefill: Bool {
        inventoryEnabled && refillAmount > 0
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
        Label(inventoryStatus.title, systemImage: inventoryStatus.systemImage)
            .font(.caption.weight(.bold))
            .foregroundStyle(inventoryStatus.color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(inventoryStatus.color.opacity(0.13), in: Capsule())
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

    private func plannedRefillAmount(days: Int) -> Double {
        switch days {
        case 7:
            if let value = item.nextSevenDaysPlannedUnits, value > 0 { return value }
        case 14:
            if let value = item.nextFourteenDaysPlannedUnits, value > 0 { return value }
        case 21:
            if let value = item.nextTwentyOneDaysPlannedUnits, value > 0 { return value }
        default:
            break
        }
        if let dailyPlannedUnits = item.dailyPlannedUnits, dailyPlannedUnits > 0 {
            return dailyPlannedUnits * Double(days)
        }
        return max(item.doseCountPerIntake, 1) * Double(days)
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
                .font(.caption.weight(.bold))
                .minimumScaleFactor(0.75)
                .foregroundStyle(refillAmount == amount ? Color.white : CaregiverUI.tealDark)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(
                    refillAmount == amount
                        ? CaregiverUI.teal
                        : AppTheme.elevatedBackground,
                    in: RoundedRectangle(cornerRadius: 11, style: .continuous)
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
            return CaregiverUI.teal
        case .low:
            return .orange
        case .out:
            return .red
        case .unconfigured:
            return .gray
        }
    }

    var systemImage: String {
        switch self {
        case .available:
            return "checkmark.circle.fill"
        case .low:
            return "exclamationmark.triangle.fill"
        case .out:
            return "xmark.octagon.fill"
        case .unconfigured:
            return "questionmark.circle.fill"
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

private enum InventoryEditAction {
    case refill
    case correction
}

struct InventoryDetailDebugPreview: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        InventoryDetailDebugPreviewHost(sessionStore: sessionStore)
    }
}

private struct InventoryDetailDebugPreviewHost: View {
    @StateObject private var viewModel: InventoryViewModel
    @State private var selectedTab: CaregiverTab = .inventory

    init(sessionStore: SessionStore) {
        _viewModel = StateObject(
            wrappedValue: InventoryViewModel(
                apiClient: APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        NavigationStack {
            InventoryDetailView(item: Self.previewItem, viewModel: viewModel)
        }
        .safeAreaInset(edge: .bottom) {
            CaregiverBottomTabBar(
                selectedTab: $selectedTab,
                hasLowStock: true,
                highlightedTab: nil
            )
            .padding(.horizontal, 12)
            .padding(.bottom, 4)
        }
    }

    private static let previewItem = InventoryItemDTO(
        medicationId: "inventory-redesign-preview",
        name: "血圧の薬 5 mg",
        isPrn: false,
        doseCountPerIntake: 1,
        inventoryEnabled: true,
        inventoryQuantity: 4,
        inventoryLowThreshold: 3,
        periodEnded: false,
        low: true,
        out: false,
        dailyPlannedUnits: 1,
        nextSevenDaysPlannedUnits: 7,
        nextFourteenDaysPlannedUnits: 14,
        nextTwentyOneDaysPlannedUnits: 21,
        daysRemaining: 4,
        refillDueDate: "7月27日"
    )
}
