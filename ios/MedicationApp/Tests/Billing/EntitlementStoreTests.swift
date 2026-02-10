import XCTest
@testable import MedicationApp

@MainActor
final class EntitlementStoreTests: XCTestCase {

    // MARK: - Initial State

    func testInitialStateIsUnknown() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Expected: EntitlementStore().state == .unknown
    }

    // MARK: - Evaluation Transitions

    func testEvaluationWithNoEntitlementSetsFree() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: No matching product in Transaction.currentEntitlements
        // When: refresh() completes
        // Then: state == .free
    }

    func testEvaluationWithMatchingProductSetsPremium() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: Transaction.currentEntitlements contains premium_unlock product
        // When: refresh() completes
        // Then: state == .premium
    }

    func testReEvaluationAfterRevocationSetsFree() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: state was .premium
        // When: refresh() runs and product no longer in currentEntitlements
        // Then: state == .free
    }

    // MARK: - Computed Properties

    func testIsPremiumReturnsTrueWhenPremium() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: state == .premium
        // Then: isPremium == true
    }

    func testIsPremiumReturnsFalseWhenFree() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: state == .free
        // Then: isPremium == false
    }

    func testIsPremiumReturnsFalseWhenUnknown() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: state == .unknown
        // Then: isPremium == false
    }

    // MARK: - Refresh Guard

    func testConcurrentRefreshesAreCoalesced() throws {
        throw XCTSkip("EntitlementStore is implemented in Phase 3 (T020).")
        // Given: refresh() is already running
        // When: another refresh() is called
        // Then: second call does not start a parallel evaluation
    }
}
