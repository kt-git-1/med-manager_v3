import SwiftUI

struct InventoryListRow: Identifiable {
    let item: InventoryItemDTO

    var id: String { item.medicationId }
}

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
        quantity: Int,
        threshold: Int
    ) async -> InventoryItemDTO? {
        guard !isUpdating else { return nil }
        isUpdating = true
        errorMessage = nil
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
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
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
        errorMessage = nil
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
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
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
        .sheet(item: $selectedItem) { item in
            InventoryDetailView(item: item, viewModel: viewModel)
        }
        .accessibilityIdentifier("InventoryListView")
    }

    private var inventoryList: some View {
        List {
            Section {
                ForEach(viewModel.items) { item in
                    Button(action: { selectedItem = item }) {
                        HStack(alignment: .center, spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.name)
                                    .font(.title3.weight(.semibold))
                                Text(
                                    String(
                                        format: NSLocalizedString(
                                            "caregiver.inventory.list.remaining",
                                            comment: "Remaining count"
                                        ),
                                        item.inventoryQuantity
                                    )
                                )
                                .font(.body)
                                .foregroundColor(.secondary)
                            }
                            Spacer()
                            inventoryBadge(for: item)
                            Image(systemName: "chevron.right")
                                .foregroundColor(.secondary)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                    }
                    .buttonStyle(.plain)
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
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.white)
        .safeAreaPadding(.bottom, 120)
    }

    @ViewBuilder
    private func inventoryBadge(for item: InventoryItemDTO) -> some View {
        if item.out {
            badge(text: "OUT", color: .red)
        } else if item.low {
            badge(text: "LOW", color: .orange)
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
}
