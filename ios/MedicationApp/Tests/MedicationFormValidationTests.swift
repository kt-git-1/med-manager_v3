import XCTest
@testable import MedicationApp

@MainActor
final class MedicationFormValidationTests: XCTestCase {
    func testValidateRequiresScheduleTimes() {
        let viewModel = MedicationFormViewModel()
        viewModel.name = "Test"
        viewModel.selectedTimeSlots = []

        let errors = viewModel.validate()

        XCTAssertTrue(errors.contains("時間は1件以上選択してください"))
    }

    func testValidateRequiresDaysForWeeklySchedule() {
        let viewModel = MedicationFormViewModel()
        viewModel.name = "Test"
        viewModel.scheduleFrequency = .weekly
        viewModel.selectedTimeSlots = [.morning]
        viewModel.selectedDays = []

        let errors = viewModel.validate()

        XCTAssertTrue(errors.contains("曜日は1つ以上選択してください"))
    }
}
