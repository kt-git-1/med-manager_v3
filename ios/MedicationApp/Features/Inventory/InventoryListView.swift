import SwiftUI

@MainActor
final class InventoryViewModel: ObservableObject {
    @Published var items: [InventoryItemDTO] = []
    @Published var isLoading = false
    @Published var isUpdating = false
    @Published var errorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func load(showLoading: Bool = true) {
        guard !isLoading, !isUpdating else { return }
        guard sessionStore.currentPatientId != nil else {
            items = []
            errorMessage = nil
            return
        }
        isLoading = showLoading
        isUpdating = !showLoading
        errorMessage = nil
        Task {
            defer {
                isLoading = false
                isUpdating = false
            }
            do {
                items = try await apiClient.fetchInventory()
            } catch {
                items = []
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    func updateSettings(
        item: InventoryItemDTO,
        enabled: Bool,
        quantity: Double?,
        threshold: Int? = nil
    ) async -> InventoryItemDTO? {
        guard !isUpdating else { return nil }
        isUpdating = true
        defer { isUpdating = false }
        do {
            let updated = try await apiClient.updateInventory(
                medicationId: item.medicationId,
                input: InventoryUpdateRequestDTO(
                    inventoryEnabled: enabled,
                    inventoryQuantity: quantity,
                    inventoryLowThreshold: threshold
                )
            )
            replaceItem(updated)
            return updated
        } catch {
            return nil
        }
    }

    func adjustInventory(
        item: InventoryItemDTO,
        reason: String,
        delta: Double?,
        absoluteQuantity: Double?
    ) async -> InventoryItemDTO? {
        guard !isUpdating else { return nil }
        isUpdating = true
        defer { isUpdating = false }
        do {
            let updated = try await apiClient.adjustInventory(
                medicationId: item.medicationId,
                input: InventoryAdjustRequestDTO(
                    reason: reason,
                    delta: delta,
                    absoluteQuantity: absoluteQuantity
                )
            )
            replaceItem(updated)
            return updated
        } catch {
            return nil
        }
    }

    private func replaceItem(_ updated: InventoryItemDTO) {
        if let index = items.firstIndex(where: { $0.medicationId == updated.medicationId }) {
            items[index] = updated
        } else {
            items.append(updated)
        }
    }
}

struct InventoryListView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    private let patientName: String?
    @StateObject private var viewModel: InventoryViewModel
    @State private var selectedItem: InventoryItemDTO?
    @State private var filter: InventoryFilter = .all
    @State private var toastMessage: String?

    init(sessionStore: SessionStore, onOpenPatients: @escaping () -> Void, patientName: String? = nil) {
        self.sessionStore = sessionStore
        self.onOpenPatients = onOpenPatients
        self.patientName = patientName
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: InventoryViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        FullScreenContainer(
            content: {
                Group {
                    if sessionStore.currentPatientId == nil {
                        InventoryEmptyStateView(onOpenPatients: onOpenPatients)
                    } else if viewModel.isLoading {
                        LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let errorMessage = viewModel.errorMessage {
                        errorSection(message: errorMessage)
                    } else if viewModel.items.isEmpty {
                        EmptyStateView(
                            title: NSLocalizedString("caregiver.inventory.list.empty.title", comment: "Inventory list empty title"),
                            message: NSLocalizedString("caregiver.inventory.list.empty.message", comment: "Inventory list empty message")
                        )
                    } else {
                        inventoryList
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .navigationTitle("")
                .navigationBarTitleDisplayMode(.inline)
            },
            overlay: viewModel.isUpdating ? AnyView(updatingOverlay) : nil
        )
        .onAppear {
            viewModel.load(showLoading: viewModel.items.isEmpty)
        }
        .onChange(of: sessionStore.currentPatientId) { _, _ in
            viewModel.load()
        }
        .onReceive(NotificationCenter.default.publisher(for: .medicationUpdated)) { _ in
            viewModel.load(showLoading: viewModel.items.isEmpty)
        }
        .sheet(item: $selectedItem, onDismiss: {
            viewModel.load(showLoading: false)
        }) { item in
            InventoryDetailView(
                item: item,
                viewModel: viewModel,
                onSaved: {
                    showToast(NSLocalizedString("caregiver.inventory.toast.saved", comment: "Inventory saved toast"))
                },
                onRefilled: {
                    showToast(NSLocalizedString("caregiver.inventory.toast.refilled", comment: "Inventory refilled toast"))
                }
            )
        }
        .sensoryFeedback(.success, trigger: toastMessage)
        .accessibilityIdentifier("InventoryListView")
    }

    private var inventoryList: some View {
        List {
            inventoryHeaderRow
            inventoryGuideRow
            filterPickerRow
            ForEach(sectionData) { section in
                if !section.items.isEmpty {
                    Section {
                        ForEach(section.items) { item in
                            inventoryRowCell(item)
                        }
                    } header: {
                        Text(NSLocalizedString(section.titleKey, comment: section.titleComment))
                            .font(.headline)
                            .foregroundStyle(.secondary)
                            .textCase(nil)
                    }
                    .listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(CaregiverUI.background)
        .safeAreaPadding(.bottom, 120)
        .refreshable {
            viewModel.load()
        }
        .overlay(alignment: .top) {
            if let toastMessage {
                Text(toastMessage)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .glassEffect(.regular, in: .capsule)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .accessibilityLabel(toastMessage)
            }
        }
    }

    private var inventoryHeaderRow: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center, spacing: 14) {
                CaregiverAvatar(name: patientName, systemImage: "shippingbox.fill")
                    .frame(width: 58, height: 58)
                VStack(alignment: .leading, spacing: 4) {
                    Text(NSLocalizedString("caregiver.inventory.title", comment: "Inventory title"))
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                    Text(patientNameLine)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                }
                Spacer(minLength: 0)
            }

            LazyVGrid(columns: metricColumns, spacing: 10) {
                inventorySummaryTile(
                    title: NSLocalizedString("caregiver.inventory.summary.needsAction", comment: "Needs action summary"),
                    value: "\(needsActionItems.count)",
                    tint: needsActionItems.isEmpty ? CaregiverUI.teal : CaregiverUI.red,
                    systemImage: needsActionItems.isEmpty ? "checkmark.circle.fill" : "exclamationmark.triangle.fill"
                )
                inventorySummaryTile(
                    title: NSLocalizedString("caregiver.inventory.summary.managed", comment: "Managed summary"),
                    value: "\(summaryConfiguredActiveItems.count)",
                    tint: CaregiverUI.blue,
                    systemImage: "archivebox.fill"
                )
                inventorySummaryTile(
                    title: NSLocalizedString("caregiver.inventory.summary.notStarted", comment: "Not started summary"),
                    value: "\(summaryUnconfiguredItems.count)",
                    tint: .gray,
                    systemImage: "questionmark.circle.fill"
                )
                inventorySummaryTile(
                    title: NSLocalizedString("caregiver.inventory.summary.ended", comment: "Ended summary"),
                    value: "\(summaryPeriodEndedItems.count)",
                    tint: CaregiverUI.orange,
                    systemImage: "calendar.badge.clock"
                )
            }
        }
        .padding(.top, 12)
        .listRowInsets(EdgeInsets(top: 10, leading: 20, bottom: 8, trailing: 20))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var inventoryGuideRow: some View {
        let primary = needsActionItems.first
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: primary == nil ? "checkmark.seal.fill" : "bell.badge.fill")
                    .font(.title2)
                    .foregroundStyle(primary == nil ? .green : CaregiverUI.orange)
                    .frame(width: 34, height: 34)
                    .background((primary == nil ? Color.green : CaregiverUI.orange).opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(primary == nil
                         ? NSLocalizedString("caregiver.inventory.guide.ok.title", comment: "Inventory guide ok title")
                         : NSLocalizedString("caregiver.inventory.guide.action.title", comment: "Inventory guide action title"))
                        .font(.headline.weight(.bold))
                    Text(guideMessage(for: primary))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            if let primary {
                Button {
                    selectedItem = primary
                } label: {
                    Label(
                        NSLocalizedString("caregiver.inventory.guide.action.button", comment: "Open action item"),
                        systemImage: "arrow.right.circle.fill"
                    )
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(CaregiverUI.orange, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(CaregiverUI.cardStroke, lineWidth: 1)
        )
        .shadow(color: CaregiverUI.cardShadow, radius: 8, y: 3)
        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 8, trailing: 16))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var filterPickerRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(InventoryFilter.allCases) { filter in
                    Button {
                        withAnimation(.snappy) {
                            self.filter = filter
                        }
                    } label: {
                        Label(filter.title, systemImage: filter.systemImage)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(self.filter == filter ? .white : filterTint(filter))
                            .padding(.horizontal, 12)
                            .frame(height: 38)
                            .background(self.filter == filter ? filterTint(filter) : Color.white, in: Capsule())
                            .overlay {
                                Capsule()
                                    .stroke(filterTint(filter).opacity(0.22), lineWidth: 1)
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 8, trailing: 0))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var sectionData: [InventorySectionData] {
        if filter == .all {
            return [
                InventorySectionData(
                    id: "configured",
                    titleKey: "caregiver.inventory.list.section",
                    titleComment: "Inventory list section",
                    items: configuredActiveItems
                ),
                InventorySectionData(
                    id: "periodEnded",
                    titleKey: "caregiver.inventory.section.periodEnded",
                    titleComment: "Period ended section",
                    items: periodEndedItems
                ),
                InventorySectionData(
                    id: "unconfigured",
                    titleKey: "caregiver.inventory.section.unconfigured",
                    titleComment: "Unconfigured section",
                    items: unconfiguredItems
                )
            ]
        }
        return [
            InventorySectionData(
                id: "filtered",
                titleKey: "caregiver.inventory.list.section",
                titleComment: "Inventory list section",
                items: filteredItems
            )
        ]
    }

    private func inventoryRowCell(_ item: InventoryItemDTO) -> some View {
        inventoryRow(for: item)
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
    }

    private struct InventorySectionData: Identifiable {
        let id: String
        let titleKey: String
        let titleComment: String
        let items: [InventoryItemDTO]
    }

    @ViewBuilder
    private func inventoryBadge(for item: InventoryItemDTO) -> some View {
        if item.out {
            badge(text: NSLocalizedString("caregiver.inventory.status.out", comment: "Out badge"), color: .red)
        } else if item.low {
            badge(text: NSLocalizedString("caregiver.inventory.status.low", comment: "Low badge"), color: .orange)
        } else if !item.inventoryEnabled {
            badge(text: NSLocalizedString("caregiver.inventory.status.unconfigured", comment: "Unconfigured badge"), color: .gray)
        }
    }

    private func badge(text: String, color: Color) -> some View {
        Text(text)
            .font(.caption.weight(.bold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.15), in: Capsule())
    }

    private var updatingOverlay: some View {
        SchedulingRefreshOverlay()
    }

    private func errorSection(message: String) -> some View {
        VStack(spacing: 12) {
            ErrorStateView(message: message)
            Button(NSLocalizedString("common.retry", comment: "Retry")) {
                viewModel.load()
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("InventoryRetryButton")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var filteredItems: [InventoryItemDTO] {
        let baseItems: [InventoryItemDTO] = viewModel.items.filter { item in
            switch filter {
            case .all:
                return true
            case .lowOnly:
                return shouldShowLowInventory(for: item)
            case .outOnly:
                return item.inventoryEnabled && item.out
            }
        }
        return sortedItems(baseItems)
    }

    private func sortedItems(_ items: [InventoryItemDTO]) -> [InventoryItemDTO] {
        items.sorted { lhs, rhs in
            if lhs.periodEnded != rhs.periodEnded {
                return !lhs.periodEnded
            }
            if lhs.out != rhs.out {
                return lhs.out
            }
            if lhs.low != rhs.low {
                return lhs.low
            }
            let lhsDays = lhs.daysRemaining ?? Int.max
            let rhsDays = rhs.daysRemaining ?? Int.max
            if lhsDays != rhsDays {
                return lhsDays < rhsDays
            }
            if lhs.inventoryQuantity != rhs.inventoryQuantity {
                return lhs.inventoryQuantity < rhs.inventoryQuantity
            }
            return lhs.name < rhs.name
        }
    }

    private var configuredActiveItems: [InventoryItemDTO] {
        filteredItems.filter { $0.inventoryEnabled && !$0.periodEnded }
    }

    private var periodEndedItems: [InventoryItemDTO] {
        filteredItems.filter { $0.periodEnded }
    }

    private var unconfiguredItems: [InventoryItemDTO] {
        filteredItems.filter { !$0.inventoryEnabled }
    }

    private var needsActionItems: [InventoryItemDTO] {
        sortedItems(
            viewModel.items.filter { item in
                item.inventoryEnabled && !item.periodEnded && (item.out || shouldShowLowInventory(for: item))
            }
        )
    }

    private var summaryConfiguredActiveItems: [InventoryItemDTO] {
        viewModel.items.filter { $0.inventoryEnabled && !$0.periodEnded }
    }

    private var summaryUnconfiguredItems: [InventoryItemDTO] {
        viewModel.items.filter { !$0.inventoryEnabled }
    }

    private var summaryPeriodEndedItems: [InventoryItemDTO] {
        viewModel.items.filter(\.periodEnded)
    }

    private var metricColumns: [GridItem] {
        [
            GridItem(.flexible(), spacing: 10),
            GridItem(.flexible(), spacing: 10)
        ]
    }

    private var patientNameLine: String {
        guard let patientName, !patientName.isEmpty else {
            return NSLocalizedString("caregiver.inventory.subtitle", comment: "Inventory subtitle")
        }
        return String(format: NSLocalizedString("caregiver.common.patient.format", comment: "Patient name format"), patientName)
    }

    private func inventoryRow(for item: InventoryItemDTO) -> some View {
        let accent = inventoryAccentColor(for: item)
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 14) {
                InventoryIllustrationView(tint: accent)
                    .frame(width: 62, height: 62)

                VStack(alignment: .leading, spacing: 6) {
                    Text(item.name)
                        .font(.title2.weight(.bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                    inlineStatusBadges(for: item)
                }
                Spacer(minLength: 0)
                if item.inventoryEnabled {
                    remainingCountView(item, accent: accent)
                }
            }

            if item.inventoryEnabled {
                VStack(alignment: .leading, spacing: 4) {
                    Text(daysRemainingText(for: item))
                        .font(.body.weight(.bold))
                        .foregroundStyle(daysRemainingColor(for: item))
                    Text(inventoryHelpText(for: item))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                HStack(spacing: 12) {
                    inventoryStepperButton(systemImage: "minus", tint: accent) {
                        Task { await adjustListInventory(item, delta: -1) }
                    }
                    Text(AppConstants.formatDecimal(item.inventoryQuantity))
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                        .frame(maxWidth: .infinity)
                    inventoryStepperButton(systemImage: "plus", tint: accent) {
                        Task { await adjustListInventory(item, delta: 1) }
                    }
                }
                .padding(6)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                if shouldShowLowInventory(for: item) || item.out {
                    Button {
                        Task { await adjustListInventory(item, delta: weeklyRefillAmount(for: item)) }
                    } label: {
                        Label(NSLocalizedString("caregiver.inventory.list.weeklyRefill.button", comment: "Weekly refill button"), systemImage: "shippingbox.fill")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(CaregiverUI.orange, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            } else {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "hand.tap.fill")
                        .foregroundStyle(.gray)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("caregiver.inventory.unconfigured.title", comment: "Unconfigured card title"))
                            .font(.body.weight(.bold))
                        Text(NSLocalizedString("caregiver.inventory.unconfigured.message", comment: "Unconfigured card message"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(12)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }

            HStack(spacing: 6) {
                Text(NSLocalizedString("caregiver.inventory.card.openDetail", comment: "Open detail hint"))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(.tertiary)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(shouldShowAttention(for: item) ? CaregiverUI.orange.opacity(0.75) : CaregiverUI.cardStroke, lineWidth: shouldShowAttention(for: item) ? 1.5 : 1)
        )
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
        .contentShape(Rectangle())
        .onTapGesture {
            selectedItem = item
        }
    }

    private func inventorySummaryTile(title: String, value: String, tint: Color, systemImage: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.caption.weight(.bold))
                Text(title)
                    .font(.caption.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .foregroundStyle(tint)

            Text(value)
                .font(.title2.weight(.bold))
                .foregroundStyle(.primary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(tint.opacity(0.22), lineWidth: 1)
        }
        .shadow(color: CaregiverUI.cardShadow, radius: 8, y: 3)
    }

    private func remainingCountView(_ item: InventoryItemDTO, accent: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(NSLocalizedString("caregiver.inventory.remaining.label", comment: "Remaining label"))
                .font(.body.weight(.semibold))
                .foregroundStyle(.primary)
            Text(AppConstants.formatDecimal(item.inventoryQuantity))
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(accent)
            Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                .font(.body.weight(.bold))
                .foregroundStyle(accent)
        }
    }

    private func inventoryStepperButton(systemImage: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(tint)
                .frame(width: 46, height: 42)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(CaregiverUI.cardStroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func adjustListInventory(_ item: InventoryItemDTO, delta: Double) async {
        guard item.inventoryEnabled else { return }
        let effectiveDelta = delta < 0 ? max(delta, -item.inventoryQuantity) : delta
        guard effectiveDelta != 0 else { return }
        let updated = await viewModel.adjustInventory(
            item: item,
            reason: delta > 0 ? "REFILL" : "CORRECTION",
            delta: effectiveDelta,
            absoluteQuantity: nil
        )
        if updated != nil {
            showToast(delta > 0
                ? NSLocalizedString("caregiver.inventory.toast.refilled", comment: "Inventory refilled toast")
                : NSLocalizedString("caregiver.inventory.toast.saved", comment: "Inventory saved toast"))
        }
    }

    private func weeklyRefillAmount(for item: InventoryItemDTO) -> Double {
        if let dailyPlannedUnits = item.dailyPlannedUnits, dailyPlannedUnits > 0 {
            return dailyPlannedUnits * 7
        }
        return max(item.doseCountPerIntake, 1) * 7
    }

    @ViewBuilder
    private func inlineStatusBadges(for item: InventoryItemDTO) -> some View {
        HStack(spacing: 6) {
            if item.isPrn {
                badge(
                    text: NSLocalizedString("medication.list.badge.prn", comment: "PRN badge"),
                    color: CaregiverUI.orange
                )
            }
            if item.out {
                badge(
                    text: NSLocalizedString("caregiver.inventory.status.out", comment: "Out badge"),
                    color: .red
                )
            } else if shouldShowLowInventory(for: item) {
                badge(
                    text: NSLocalizedString("caregiver.inventory.status.low", comment: "Low badge"),
                    color: .orange
                )
            } else if !item.inventoryEnabled {
                badge(
                    text: NSLocalizedString("caregiver.inventory.status.unconfigured", comment: "Unconfigured badge"),
                    color: .gray
                )
            }
        }
    }

    private func daysRemainingText(for item: InventoryItemDTO) -> String {
        if item.isPrn {
            return NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
        }
        if item.periodEnded {
            return NSLocalizedString("caregiver.inventory.plan.ended", comment: "Plan ended")
        }
        guard let daysRemaining = item.daysRemaining else {
            return "—"
        }
        return String(
            format: NSLocalizedString(
                "caregiver.inventory.plan.daysRemaining.short",
                comment: "Remaining days short"
            ),
            daysRemaining
        )
    }

    private func inventoryHelpText(for item: InventoryItemDTO) -> String {
        if item.periodEnded {
            return NSLocalizedString("caregiver.inventory.help.periodEnded", comment: "Period ended help")
        }
        if item.out {
            return NSLocalizedString("caregiver.inventory.help.out", comment: "Out help")
        }
        if shouldShowLowInventory(for: item) {
            if item.daysRemaining != nil {
                return refillDueText(for: item)
            }
            return NSLocalizedString("caregiver.inventory.help.low", comment: "Low help")
        }
        if item.isPrn {
            return NSLocalizedString("caregiver.inventory.help.prn", comment: "PRN help")
        }
        return NSLocalizedString("caregiver.inventory.help.available", comment: "Available help")
    }

    private func shouldShowLowInventory(for item: InventoryItemDTO) -> Bool {
        item.inventoryEnabled && !item.periodEnded && item.low
    }

    private func shouldShowAttention(for item: InventoryItemDTO) -> Bool {
        item.inventoryEnabled && !item.periodEnded && (item.out || shouldShowLowInventory(for: item))
    }

    private func daysRemainingColor(for item: InventoryItemDTO) -> Color {
        if shouldShowLowInventory(for: item) {
            return .red
        }
        return .secondary
    }

    private func refillDueText(for item: InventoryItemDTO) -> String {
        let label = NSLocalizedString("caregiver.inventory.plan.refillDue", comment: "Refill due label")
        if let refillDueDate = item.refillDueDate, !refillDueDate.isEmpty {
            return "\(label)：\(refillDueDate)"
        }
        return label
    }

    private func guideMessage(for item: InventoryItemDTO?) -> String {
        guard let item else {
            return NSLocalizedString("caregiver.inventory.guide.ok.message", comment: "Inventory guide ok message")
        }
        if item.out {
            return String(
                format: NSLocalizedString("caregiver.inventory.guide.out.message", comment: "Inventory guide out message"),
                item.name
            )
        }
        return String(
            format: NSLocalizedString("caregiver.inventory.guide.low.message", comment: "Inventory guide low message"),
            item.name,
            lowInventoryStatusText(for: item)
        )
    }

    private func lowInventoryStatusText(for item: InventoryItemDTO) -> String {
        if item.daysRemaining != nil {
            return daysRemainingText(for: item)
        }
        return String(
            format: NSLocalizedString("caregiver.inventory.remaining.count", comment: "Remaining count"),
            AppConstants.formatDecimal(item.inventoryQuantity)
        )
    }

    private func inventoryAccentColor(for item: InventoryItemDTO) -> Color {
        if shouldShowAttention(for: item) {
            return CaregiverUI.orange
        }
        return CaregiverUI.blue
    }

    private func filterTint(_ filter: InventoryFilter) -> Color {
        switch filter {
        case .all:
            return CaregiverUI.teal
        case .lowOnly:
            return CaregiverUI.orange
        case .outOnly:
            return CaregiverUI.red
        }
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task {
            try? await Task.sleep(for: .seconds(AppConstants.toastDuration))
            await MainActor.run {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
    }
}

private enum InventoryFilter: String, CaseIterable, Identifiable {
    case all
    case lowOnly
    case outOnly

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all:
            return NSLocalizedString("caregiver.inventory.filter.all", comment: "All filter")
        case .lowOnly:
            return NSLocalizedString("caregiver.inventory.filter.low", comment: "Low inventory filter")
        case .outOnly:
            return NSLocalizedString("caregiver.inventory.filter.out", comment: "Out of stock filter")
        }
    }

    var systemImage: String {
        switch self {
        case .all:
            return "list.bullet"
        case .lowOnly:
            return "exclamationmark.triangle.fill"
        case .outOnly:
            return "xmark.octagon.fill"
        }
    }
}

private struct InventoryIllustrationView: View {
    let tint: Color

    var body: some View {
        ZStack {
            Circle()
                .fill(tint.opacity(0.14))
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .fill(Color.white)
                .frame(width: 40, height: 34)
                .offset(y: 4)
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .stroke(tint, lineWidth: 2.4)
                .frame(width: 40, height: 34)
                .offset(y: 4)
            Path { path in
                path.move(to: CGPoint(x: 22, y: 25))
                path.addLine(to: CGPoint(x: 31, y: 18))
                path.addLine(to: CGPoint(x: 40, y: 25))
            }
            .stroke(tint, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            Capsule()
                .fill(tint.opacity(0.86))
                .frame(width: 22, height: 9)
                .offset(y: -18)
        }
    }
}
