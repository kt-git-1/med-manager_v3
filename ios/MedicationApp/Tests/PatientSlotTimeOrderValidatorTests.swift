import XCTest
@testable import MedicationApp

final class PatientSlotTimeOrderValidatorTests: XCTestCase {
    func testAcceptsStrictlyIncreasingSlotTimes() {
        let slotTimes = PatientSlotTimesDTO(
            morning: "08:00",
            noon: "12:30",
            evening: "19:00",
            bedtime: "23:00"
        )

        XCTAssertNil(PatientSlotTimeOrderValidator.validationMessage(for: slotTimes))
    }

    func testRejectsNoonEarlierThanMorning() {
        let slotTimes = PatientSlotTimesDTO(
            morning: "08:00",
            noon: "07:30",
            evening: "19:00",
            bedtime: "23:00"
        )

        XCTAssertEqual(
            PatientSlotTimeOrderValidator.validationMessage(for: slotTimes),
            "昼の時間は朝より後に設定してください。"
        )
    }

    func testRejectsEqualAdjacentTimes() {
        let slotTimes = PatientSlotTimesDTO(
            morning: "08:00",
            noon: "08:00",
            evening: "19:00",
            bedtime: "23:00"
        )

        XCTAssertEqual(
            PatientSlotTimeOrderValidator.validationMessage(for: slotTimes),
            "昼の時間は朝より後に設定してください。"
        )
    }

    func testRejectsEveningEarlierThanNoon() {
        let slotTimes = PatientSlotTimesDTO(
            morning: "08:00",
            noon: "13:00",
            evening: "12:00",
            bedtime: "23:00"
        )

        XCTAssertEqual(
            PatientSlotTimeOrderValidator.validationMessage(for: slotTimes),
            "夜の時間は昼より後に設定してください。"
        )
    }

    func testRejectsBedtimeEarlierThanEvening() {
        let slotTimes = PatientSlotTimesDTO(
            morning: "08:00",
            noon: "13:00",
            evening: "19:00",
            bedtime: "18:00"
        )

        XCTAssertEqual(
            PatientSlotTimeOrderValidator.validationMessage(for: slotTimes),
            "眠前の時間は夜より後に設定してください。"
        )
    }
}
