import XCTest
@testable import MedicationApp

final class MedicationFormViewModelTests: XCTestCase {
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
            doseCountPerIntake: 1,
            dosageStrengthValue: 10,
            dosageStrengthUnit: "mg",
            notes: nil,
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
            doseCountPerIntake: 1,
            dosageStrengthValue: 10,
            dosageStrengthUnit: "mg",
            notes: nil,
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
}
