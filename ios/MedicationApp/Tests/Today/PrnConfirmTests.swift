import XCTest
@testable import MedicationApp

@MainActor
final class PrnConfirmTests: XCTestCase {
    func testConfirmDialogTriggersSingleApiCall() throws {
        throw XCTSkip("PRN confirm debounce behavior is implemented in later tasks.")
    }
}
