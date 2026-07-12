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
    private let feature: PremiumFeature
    private let surface: AnalyticsSurface
    private static let premiumProductId = "com.yourcompany.medicationapp.premium_unlock"

    var isRefreshing: Bool { entitlementStore.isRefreshing }

    // MARK: - Init

    init(
        entitlementStore: EntitlementStore,
        feature: PremiumFeature,
        surface: AnalyticsSurface
    ) {
        self.entitlementStore = entitlementStore
        self.feature = feature
        self.surface = surface
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
        AnalyticsService.shared.logPurchaseStarted(feature: feature, surface: surface)
        do {
            let result = try await entitlementStore.purchase(product: product)
            AnalyticsService.shared.logPurchaseResult(result, feature: feature, surface: surface)
            if result == .success {
                AnalyticsService.shared.logPremiumActivated(source: .purchase)
            }
        } catch {
            AnalyticsService.shared.logPurchaseResult(.failed, feature: feature, surface: surface)
            // Purchase errors are handled silently (user cancelled, network, etc.)
            print("PaywallViewModel: purchase failed: \(error.localizedDescription)")
        }
    }

    func restore() async {
        AnalyticsService.shared.logRestoreStarted(surface: surface)
        let result = await entitlementStore.restore()
        AnalyticsService.shared.logRestoreResult(result, surface: surface)
        if result == .success {
            AnalyticsService.shared.logPremiumActivated(source: .restore)
        }
    }
}
