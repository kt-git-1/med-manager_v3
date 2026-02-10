import Foundation
import StoreKit
import Observation

@Observable
@MainActor
final class PaywallViewModel {

    // MARK: - State

    private(set) var product: Product?
    private(set) var loadError: String?
    private(set) var isLoadingProduct = false

    // MARK: - Dependencies

    private let entitlementStore: EntitlementStore
    private static let premiumProductId = "com.yourcompany.medicationapp.premium_unlock"

    var isRefreshing: Bool { entitlementStore.isRefreshing }

    // MARK: - Init

    init(entitlementStore: EntitlementStore) {
        self.entitlementStore = entitlementStore
    }

    // MARK: - Product Loading

    func loadProduct() async {
        guard !isLoadingProduct else { return }
        isLoadingProduct = true
        loadError = nil
        defer { isLoadingProduct = false }

        do {
            let products = try await Product.products(for: [Self.premiumProductId])
            product = products.first
            if product == nil {
                loadError = NSLocalizedString("billing.paywall.error", comment: "Load error")
            }
        } catch {
            loadError = NSLocalizedString("billing.paywall.error", comment: "Load error")
        }
    }

    func retryLoad() {
        Task { await loadProduct() }
    }

    // MARK: - Purchase / Restore

    func purchase() async {
        guard let product else { return }
        do {
            try await entitlementStore.purchase(product: product)
        } catch {
            // Purchase errors are handled silently (user cancelled, network, etc.)
            print("PaywallViewModel: purchase failed: \(error.localizedDescription)")
        }
    }

    func restore() async {
        await entitlementStore.restore()
    }
}
