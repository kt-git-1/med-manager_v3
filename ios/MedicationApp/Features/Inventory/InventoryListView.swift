import SwiftUI

@MainActor
final class InventoryViewModel: ObservableObject {
    @Published var items: [InventoryItemDTO] = []
    @Published var isUpdating = false
    @Published var errorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func load() {
        guard !isUpdating else { return }
        guard sessionStore.currentPatientId != nil else {
            items = []
            errorMessage = nil
            return
        }
        isUpdating = true
        errorMessage = nil
        Task {
            defer { isUpdating = false }
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
        quantity: Int?,
        threshold: Int
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
        delta: Int?,
        absoluteQuantity: Int?
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
    @StateObject private var viewModel: InventoryViewModel
    @State private var selectedItem: InventoryItemDTO?
    @State private var filter: InventoryFilter = .all
    @State private var toastMessage: String?

    init(sessionStore: SessionStore, onOpenPatients: @escaping () -> Void) {
        self.sessionStore = sessionStore
        self.onOpenPatients = onOpenPatients
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
                .navigationTitle(NSLocalizedString("caregiver.tabs.inventory", comment: "Inventory tab"))
                .navigationBarTitleDisplayMode(.large)
            },
            overlay: viewModel.isUpdating ? AnyView(updatingOverlay) : nil
        )
        .onAppear {
            viewModel.load()
        }
        .onChange(of: sessionStore.currentPatientId) { _, _ in
            viewModel.load()
        }
        .onReceive(NotificationCenter.default.publisher(for: .medicationUpdated)) { _ in
            viewModel.load()
        }
        .sheet(item: $selectedItem, onDismiss: {
            viewModel.load()
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
        VStack(spacing: 12) {
            Picker(
                NSLocalizedString("caregiver.inventory.filter.all", comment: "All filter"),
                selection: $filter
            ) {
                ForEach(InventoryFilter.allCases) { filter in
                    Text(filter.title).tag(filter)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)

            List {
                if filter == .all {
                    if !configuredActiveItems.isEmpty {
                        Section {
                            ForEach(configuredActiveItems) { item in
                                inventoryRow(for: item)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                    .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("caregiver.inventory.list.section", comment: "Inventory list section"))
                                .font(.headline)
                                .foregroundColor(.secondary)
                                .textCase(nil)
                        }
                        .listRowSeparator(.hidden)
                    }

                    if !periodEndedItems.isEmpty {
                        Section {
                            ForEach(periodEndedItems) { item in
                                inventoryRow(for: item)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                    .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("caregiver.inventory.section.periodEnded", comment: "Period ended section"))
                                .font(.headline)
                                .foregroundColor(.secondary)
                                .textCase(nil)
                        }
                        .listRowSeparator(.hidden)
                    }

                    if !unconfiguredItems.isEmpty {
                        Section {
                            ForEach(unconfiguredItems) { item in
                                inventoryRow(for: item)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                    .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("caregiver.inventory.section.unconfigured", comment: "Unconfigured section"))
                                .font(.headline)
                                .foregroundColor(.secondary)
                                .textCase(nil)
                        }
                        .listRowSeparator(.hidden)
                    }
                } else {
                    Section {
                        ForEach(filteredItems) { item in
                            inventoryRow(for: item)
                                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                .listRowSeparator(.hidden)
                        }
                    } header: {
                        Text(NSLocalizedString("caregiver.inventory.list.section", comment: "Inventory list section"))
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .textCase(nil)
                    }
                    .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Color.white)
            .safeAreaPadding(.bottom, 120)
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
        }
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
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.15), in: Capsule())
    }

    private var updatingOverlay: some View {
        ZStack {
            Color.black.opacity(0.2)
                .ignoresSafeArea()
            VStack {
                Spacer()
                LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                    .padding(16)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(radius: 6)
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .accessibilityIdentifier("InventoryUpdatingOverlay")
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
                return item.inventoryEnabled && item.low
            case .outOnly:
                return item.inventoryEnabled && item.out
            }
        }
        return baseItems.sorted { lhs, rhs in
            if lhs.periodEnded != rhs.periodEnded {
                return !lhs.periodEnded
            }
            let lhsRank = sortRank(for: lhs)
            let rhsRank = sortRank(for: rhs)
            if lhsRank != rhsRank {
                return lhsRank < rhsRank
            }
            if lhs.inventoryQuantity != rhs.inventoryQuantity {
                return lhs.inventoryQuantity < rhs.inventoryQuantity
            }
            return lhs.name < rhs.name
        }
    }

    private var configuredActiveItems: [InventoryItemDTO] {
        filteredItems
            .filter { $0.inventoryEnabled && !$0.periodEnded }
            .sorted { lhs, rhs in
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

    private var periodEndedItems: [InventoryItemDTO] {
        filteredItems.filter { $0.periodEnded }
    }

    private var unconfiguredItems: [InventoryItemDTO] {
        filteredItems.filter { !$0.inventoryEnabled }
    }

    private func sortRank(for item: InventoryItemDTO) -> Int {
        if !item.inventoryEnabled {
            return 3
        }
        if item.out {
            return 0
        }
        if item.low {
            return 1
        }
        return 2
    }

    private func inventoryRow(for item: InventoryItemDTO) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center, spacing: 8) {
                    Text(item.name)
                        .font(.title3.weight(.semibold))
                    inlineStatusBadge(for: item)
                }
                Text(daysRemainingText(for: item))
                    .font(.subheadline)
                    .foregroundColor(daysRemainingColor(for: item))
                if shouldShowLowDaysWarning(for: item) {
                    Text(refillDueText(for: item))
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.red)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 8) {
                if item.inventoryEnabled {
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("\(item.inventoryQuantity)")
                            .font(.title2.weight(.bold))
                        Text(NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            Image(systemName: "chevron.right")
                .foregroundColor(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(shouldShowLowDaysWarning(for: item) ? Color.red.opacity(0.6) : Color.clear, lineWidth: 2)
        )
        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
        .contentShape(Rectangle())
        .onTapGesture {
            selectedItem = item
        }
    }

    @ViewBuilder
    private func inlineStatusBadge(for item: InventoryItemDTO) -> some View {
        if item.out {
            badge(
                text: NSLocalizedString("caregiver.inventory.status.out", comment: "Out badge"),
                color: .red
            )
        } else if item.inventoryEnabled, item.low {
            badge(
                text: NSLocalizedString("caregiver.inventory.status.low", comment: "Low badge"),
                color: .orange
            )
        } else if !item.inventoryEnabled {
            badge(
                text: NSLocalizedString("caregiver.inventory.status.unconfigured", comment: "Unconfigured badge"),
                color: .gray
            )
        } else {
            EmptyView()
        }
    }

    private func daysRemainingText(for item: InventoryItemDTO) -> String {
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

    private func shouldShowLowDaysWarning(for item: InventoryItemDTO) -> Bool {
        guard item.inventoryEnabled, !item.periodEnded, let daysRemaining = item.daysRemaining else {
            return false
        }
        return daysRemaining <= 3
    }

    private func daysRemainingColor(for item: InventoryItemDTO) -> Color {
        if shouldShowLowDaysWarning(for: item) {
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
}
