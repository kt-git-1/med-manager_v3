import XCTest
@testable import MedicationApp

final class SchedulingRefreshCoordinatorTests: XCTestCase {
    func testOnlySettingsChangesBlockInteraction() {
        XCTAssertFalse(SchedulingRefreshCoordinator.RefreshTrigger.appLaunch.blocksInteraction)
        XCTAssertFalse(SchedulingRefreshCoordinator.RefreshTrigger.appForeground.blocksInteraction)
        XCTAssertFalse(SchedulingRefreshCoordinator.RefreshTrigger.doseRecorded.blocksInteraction)
        XCTAssertTrue(SchedulingRefreshCoordinator.RefreshTrigger.settingsChange.blocksInteraction)
    }
}
