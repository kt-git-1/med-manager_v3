import XCTest

final class PatientNoBillingUITests: XCTestCase {

    // MARK: - Patient Mode Has Zero Billing UI

    func testPatientSettingsDoesNotContainUpgradeButton() throws {
        throw XCTSkip("Patient mode guarantee is verified in Phase 3 (T026).")
        // Given: App launched in patient mode
        // When: Settings tab is opened
        // Then: No element with accessibilityIdentifier "billing.premium.upgrade" exists
    }

    func testPatientSettingsDoesNotContainRestoreButton() throws {
        throw XCTSkip("Patient mode guarantee is verified in Phase 3 (T026).")
        // Given: App launched in patient mode
        // When: Settings tab is opened
        // Then: No element with accessibilityIdentifier "billing.premium.restore" exists
    }

    func testNoBillingIdentifiersAcrossPatientTabs() throws {
        throw XCTSkip("Patient mode guarantee is verified in Phase 3 (T026).")
        // Given: App launched in patient mode
        // When: All tab bar items are tapped and screens inspected
        // Then: No accessibility identifiers starting with "billing." are found
    }
}
