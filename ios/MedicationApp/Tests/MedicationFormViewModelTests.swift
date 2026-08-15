import XCTest
@testable import MedicationApp

@MainActor
final class MedicationFormViewModelTests: XCTestCase {
    private func makeViewModelWithDefaultSlots() -> MedicationFormViewModel {
        let defaults = UserDefaults(suiteName: "MedicationFormViewModelTests.defaults")!
        defaults.removePersistentDomain(forName: "MedicationFormViewModelTests.defaults")
        let preferencesStore = NotificationPreferencesStore(defaults: defaults)
        preferencesStore.setSlotTime(.morning, hour: 8, minute: 0)
        preferencesStore.setSlotTime(.noon, hour: 13, minute: 0)
        preferencesStore.setSlotTime(.evening, hour: 18, minute: 0)
        preferencesStore.setSlotTime(.bedtime, hour: 21, minute: 0)

        let sessionStore = SessionStore(userDefaults: defaults)
        let apiClient = APIClient(
            baseURL: URL(string: "http://localhost:3000")!,
            sessionStore: sessionStore
        )
        return MedicationFormViewModel(
            apiClient: apiClient,
            sessionStore: sessionStore,
            preferencesStore: preferencesStore
        )
    }

    func testMedicationListRequestRequiresPatientIdForCaregiver() {
        let userDefaults = UserDefaults(suiteName: "MedicationFormViewModelTests")!
        userDefaults.removePersistentDomain(forName: "MedicationFormViewModelTests")
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)

        XCTAssertThrowsError(try apiClient.makeMedicationListRequest(patientId: nil)) { error in
            guard case APIError.validation = error else {
                return XCTFail("Expected validation error")
            }
        }
    }

    func testMedicationListRequestUsesCurrentPatientIdForCaregiver() throws {
        let userDefaults = UserDefaults(suiteName: "MedicationFormViewModelTests")!
        userDefaults.removePersistentDomain(forName: "MedicationFormViewModelTests")
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        sessionStore.setCurrentPatientId("patient-2")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)

        let request = try apiClient.makeMedicationListRequest(patientId: "patient-1")
        let queryItems = URLComponents(url: try XCTUnwrap(request.url), resolvingAgainstBaseURL: false)?.queryItems
        XCTAssertEqual(queryItems?.first(where: { $0.name == "patientId" })?.value, "patient-2")
    }

    func testCreateMedicationRequestIncludesPatientIdInBody() throws {
        let userDefaults = UserDefaults(suiteName: "MedicationFormViewModelTests")!
        userDefaults.removePersistentDomain(forName: "MedicationFormViewModelTests")
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        sessionStore.setCurrentPatientId("patient-2")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let input = MedicationCreateRequestDTO(
            patientId: "patient-1",
            name: "Test",
            dosageText: "10 mg",
            doseCountPerIntake: 1.0,
            dosageStrengthValue: 10,
            dosageStrengthUnit: "mg",
            notes: nil,
            isPrn: false,
            prnInstructions: nil,
            startDate: Date(timeIntervalSince1970: 0),
            endDate: nil,
            inventoryCount: nil,
            inventoryUnit: nil
        )

        let request = try apiClient.makeMedicationCreateRequest(input: input)
        let body = try XCTUnwrap(request.httpBody)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(json["patientId"] as? String, "patient-2")
    }

    func testUpdateAndDeleteMedicationRequestsIncludePatientIdQuery() throws {
        let userDefaults = UserDefaults(suiteName: "MedicationFormViewModelTests")!
        userDefaults.removePersistentDomain(forName: "MedicationFormViewModelTests")
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        sessionStore.setCurrentPatientId("patient-2")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let updateInput = MedicationUpdateRequestDTO(
            name: "Updated",
            dosageText: "10 mg",
            doseCountPerIntake: 1.0,
            dosageStrengthValue: 10,
            dosageStrengthUnit: "mg",
            notes: nil,
            isPrn: false,
            prnInstructions: nil,
            startDate: Date(timeIntervalSince1970: 0),
            endDate: nil,
            inventoryCount: nil,
            inventoryUnit: nil
        )

        let updateRequest = try apiClient.makeMedicationUpdateRequest(
            id: "med-1",
            patientId: "patient-1",
            input: updateInput
        )
        let updateItems = URLComponents(url: try XCTUnwrap(updateRequest.url), resolvingAgainstBaseURL: false)?
            .queryItems
        XCTAssertEqual(updateItems?.first(where: { $0.name == "patientId" })?.value, "patient-2")

        let deleteRequest = try apiClient.makeMedicationDeleteRequest(id: "med-1", patientId: "patient-1")
        let deleteItems = URLComponents(url: try XCTUnwrap(deleteRequest.url), resolvingAgainstBaseURL: false)?
            .queryItems
        XCTAssertEqual(deleteItems?.first(where: { $0.name == "patientId" })?.value, "patient-2")
    }

    func testScheduleSerializationOrdersTimesAndDays() {
        let viewModel = makeViewModelWithDefaultSlots()
        viewModel.name = "Test"
        viewModel.selectedTimeSlots = [.bedtime, .morning, .evening]
        viewModel.selectedDays = [.fri, .mon, .wed]
        viewModel.scheduleFrequency = .weekly

        XCTAssertEqual(viewModel.scheduleTimes(), ["morning", "evening", "bedtime"])
        XCTAssertEqual(viewModel.scheduleDays(), ["MON", "WED", "FRI"])
    }

    func testApplyRegimenPrefillsSchedule() {
        let viewModel = makeViewModelWithDefaultSlots()
        let regimen = RegimenDTO(
            id: "reg-1",
            patientId: "patient-1",
            medicationId: "med-1",
            timezone: "UTC",
            startDate: Date(timeIntervalSince1970: 0),
            endDate: nil,
            times: ["noon", "evening"],
            daysOfWeek: [],
            enabled: true
        )

        viewModel.applyRegimen(regimen)

        XCTAssertEqual(viewModel.scheduleFrequency, .daily)
        XCTAssertTrue(viewModel.selectedTimeSlots.contains(.noon))
        XCTAssertTrue(viewModel.selectedTimeSlots.contains(.evening))
        XCTAssertTrue(viewModel.selectedDays.isEmpty)
    }

    func testDailySupplyDaysCalculatesInitialInventory() {
        let viewModel = makeViewModelWithDefaultSlots()
        viewModel.doseCountPerIntake = "1"
        viewModel.selectedTimeSlots = [.morning, .evening]
        viewModel.scheduleFrequency = .daily
        viewModel.supplyDays = "30"

        viewModel.recalculateInventoryFromSupplyDays()

        XCTAssertEqual(viewModel.calculatedInventoryCount, 60)
        XCTAssertEqual(viewModel.inventoryCount, "60")
        XCTAssertEqual(viewModel.inventoryCalculationDescription, "1錠 × 2回 × 30日")
    }

    func testFractionalDoseSupplyCalculationKeepsDecimalQuantity() {
        let viewModel = makeViewModelWithDefaultSlots()
        viewModel.doseCountPerIntake = "0.5"
        viewModel.selectedTimeSlots = [.morning, .noon, .evening]
        viewModel.supplyDays = "7"

        viewModel.recalculateInventoryFromSupplyDays()

        XCTAssertEqual(viewModel.calculatedInventoryCount, 10.5)
        XCTAssertEqual(viewModel.inventoryCount, "10.5")
    }

    func testWeeklySupplyCalculationCountsOnlySelectedWeekdays() throws {
        let viewModel = makeViewModelWithDefaultSlots()
        viewModel.startDate = try XCTUnwrap(
            Calendar(identifier: .gregorian).date(from: DateComponents(year: 2026, month: 7, day: 20))
        )
        viewModel.doseCountPerIntake = "1"
        viewModel.selectedTimeSlots = [.morning, .evening]
        viewModel.scheduleFrequency = .weekly
        viewModel.selectedDays = [.mon, .wed, .fri]
        viewModel.supplyDays = "7"

        viewModel.recalculateInventoryFromSupplyDays()

        XCTAssertEqual(viewModel.calculatedInventoryCount, 6)
        XCTAssertEqual(viewModel.inventoryCount, "6")
        XCTAssertEqual(viewModel.inventoryCalculationDescription, "1錠 × 2回 × 服用日3日")
    }

    func testPrnMedicationDoesNotAutoCalculateInventory() {
        let viewModel = makeViewModelWithDefaultSlots()
        viewModel.isPrn = true
        viewModel.doseCountPerIntake = "1"
        viewModel.selectedTimeSlots = [.morning]
        viewModel.supplyDays = "30"

        viewModel.recalculateInventoryFromSupplyDays()

        XCTAssertNil(viewModel.calculatedInventoryCount)
        XCTAssertTrue(viewModel.inventoryCount.isEmpty)
    }

    func testClearingSupplyDaysClearsPreviouslyCalculatedInventory() {
        let viewModel = makeViewModelWithDefaultSlots()
        viewModel.doseCountPerIntake = "1"
        viewModel.selectedTimeSlots = [.morning, .evening]
        viewModel.supplyDays = "30"
        viewModel.recalculateInventoryFromSupplyDays()
        XCTAssertEqual(viewModel.inventoryCount, "60")

        viewModel.supplyDays = ""
        viewModel.recalculateInventoryFromSupplyDays()

        XCTAssertTrue(viewModel.inventoryCount.isEmpty)
    }
}
