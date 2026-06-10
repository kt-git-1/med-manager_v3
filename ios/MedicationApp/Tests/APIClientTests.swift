import XCTest
@testable import MedicationApp

@MainActor
final class APIClientTests: XCTestCase {
    private let suiteName = "APIClientTests"

    override func setUp() {
        super.setUp()
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testCreateRegimenRequestEncodesScheduleFields() throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let input = RegimenCreateRequestDTO(
            timezone: "UTC",
            startDate: Date(timeIntervalSince1970: 0),
            endDate: nil,
            times: ["morning", "evening"],
            daysOfWeek: ["MON", "FRI"]
        )

        let request = try apiClient.makeRegimenCreateRequest(medicationId: "med-1", input: input)
        let body = try XCTUnwrap(request.httpBody)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])

        XCTAssertEqual(json["timezone"] as? String, "UTC")
        XCTAssertEqual(json["times"] as? [String], ["morning", "evening"])
        XCTAssertEqual(json["daysOfWeek"] as? [String], ["MON", "FRI"])
    }

    func testUpdateRegimenRequestEncodesScheduleFields() throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let input = RegimenUpdateRequestDTO(
            timezone: "Asia/Tokyo",
            startDate: Date(timeIntervalSince1970: 0),
            endDate: nil,
            times: ["noon"],
            daysOfWeek: [],
            enabled: true
        )

        let request = try apiClient.makeRegimenUpdateRequest(id: "reg-1", input: input)
        let body = try XCTUnwrap(request.httpBody)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])

        XCTAssertEqual(json["timezone"] as? String, "Asia/Tokyo")
        XCTAssertEqual(json["times"] as? [String], ["noon"])
        XCTAssertEqual(json["daysOfWeek"] as? [String], [])
    }

    func testUnauthorizedClearsCaregiverSession() throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let response = try XCTUnwrap(HTTPURLResponse(
            url: URL(string: "http://localhost:3000/api/patients")!,
            statusCode: 401,
            httpVersion: nil,
            headerFields: nil
        ))

        XCTAssertThrowsError(try apiClient.mapErrorIfNeeded(response: response, data: Data())) { error in
            guard case APIError.unauthorized = error else {
                XCTFail("Expected unauthorized error, got \(error)")
                return
            }
        }
        XCTAssertNil(sessionStore.caregiverToken)
    }

    func testForbiddenDoesNotClearCaregiverSession() throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let response = try XCTUnwrap(HTTPURLResponse(
            url: URL(string: "http://localhost:3000/api/patients")!,
            statusCode: 403,
            httpVersion: nil,
            headerFields: nil
        ))

        XCTAssertThrowsError(try apiClient.mapErrorIfNeeded(response: response, data: Data())) { error in
            guard case APIError.forbidden = error else {
                XCTFail("Expected forbidden error, got \(error)")
                return
            }
        }
        XCTAssertEqual(sessionStore.caregiverToken, "caregiver-token")
        XCTAssertEqual(sessionStore.mode, .caregiver)
    }
}
