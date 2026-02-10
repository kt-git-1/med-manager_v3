import Foundation
import StoreKit
import Observation

// MARK: - Entitlement State

enum EntitlementState: Sendable {
    case unknown
    case free
    case premium
}

// MARK: - Entitlement Store

@Observable
@MainActor
final class EntitlementStore {

    // MARK: - Public State

    private(set) var state: EntitlementState = .unknown
    private(set) var isRefreshing = false

    var isPremium: Bool { state == .premium }

    // MARK: - Dependencies

    private let productId = "com.yourcompany.medicationapp.premium_unlock"
    private var apiClient: APIClient?
    private var refreshTask: Task<Void, Never>?

    // MARK: - Init

    init() {}

    func configure(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    // MARK: - Refresh (StoreKit2 local check)

    func refresh() async {
        // Coalesce concurrent refreshes
        guard refreshTask == nil else { return }

        let task = Task { @MainActor in
            isRefreshing = true
            defer {
                isRefreshing = false
                refreshTask = nil
            }

            var foundPremium = false
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result {
                    if transaction.productID == productId {
                        foundPremium = true
                        break
                    }
                }
            }
            state = foundPremium ? .premium : .free
        }
        refreshTask = task
        await task.value
    }

    // MARK: - Purchase

    func purchase(product: Product) async throws {
        isRefreshing = true
        defer { isRefreshing = false }

        let result = try await product.purchase()

        switch result {
        case .success(let verification):
            if case .verified(let transaction) = verification {
                await transaction.finish()

                // Refresh local state
                await refresh()

                // Fire-and-forget server claim using JWS from verification result
                let jwsString = verification.jwsRepresentation
                claimOnServer(
                    productID: transaction.productID,
                    jwsRepresentation: jwsString,
                    environmentValue: String(describing: transaction.environment)
                )
            }
        case .userCancelled:
            break
        case .pending:
            break
        @unknown default:
            break
        }
    }

    // MARK: - Restore

    func restore() async {
        isRefreshing = true
        defer { isRefreshing = false }

        try? await AppStore.sync()
        await refresh()
    }

    // MARK: - Server Claim (fire-and-forget)

    private func claimOnServer(productID: String, jwsRepresentation: String, environmentValue: String) {
        guard let apiClient else { return }

        Task { @MainActor in
            do {
                let request = ClaimRequest(
                    productId: productID,
                    signedTransactionInfo: jwsRepresentation,
                    environment: environmentValue
                )
                _ = try await apiClient.claimEntitlement(request)
            } catch {
                // Tolerate server claim failure — local entitlement is authoritative
                print("EntitlementStore: server claim failed: \(error.localizedDescription)")
            }
        }
    }
}
