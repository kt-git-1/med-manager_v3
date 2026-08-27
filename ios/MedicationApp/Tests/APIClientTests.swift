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
        APIClientMockURLProtocol.requestHandler = nil
        APIClientMockURLProtocol.shouldHang = false
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testExchangeLinkCodeNeverSendsStalePatientAuthorization() async throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(
            userDefaults: userDefaults,
            secureStorage: APIClientTestSecureStorage()
        )
        sessionStore.setMode(.patient)
        sessionStore.savePatientToken("stale-patient-token")
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [APIClientMockURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        var receivedAuthorization: String?
        APIClientMockURLProtocol.requestHandler = { request in
            receivedAuthorization = request.value(forHTTPHeaderField: "Authorization")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["content-type": "application/json"]
            )!
            let data = Data(
                #"{"data":{"patientSessionToken":"new-patient-token","expiresAt":"2027-07-13T00:00:00Z"}}"#.utf8
            )
            return (response, data)
        }
        let apiClient = APIClient(
            baseURL: URL(string: "http://localhost:3000")!,
            sessionStore: sessionStore,
            urlSession: urlSession,
            linkURLSession: urlSession
        )

        let linked = try await apiClient.exchangeLinkCode(code: "123456")

        XCTAssertEqual(linked.patientSessionToken, "new-patient-token")
        XCTAssertNil(receivedAuthorization)
    }

    func testUnauthorizedLinkExchangeDoesNotDeleteExistingPatientSession() async throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(
            userDefaults: userDefaults,
            secureStorage: APIClientTestSecureStorage()
        )
        sessionStore.setMode(.patient)
        sessionStore.savePatientToken("existing-patient-token")
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [APIClientMockURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        APIClientMockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 401,
                httpVersion: nil,
                headerFields: ["content-type": "application/json"]
            )!
            return (response, Data(#"{"error":"Unauthorized"}"#.utf8))
        }
        let apiClient = APIClient(
            baseURL: URL(string: "http://localhost:3000")!,
            sessionStore: sessionStore,
            urlSession: urlSession,
            linkURLSession: urlSession
        )

        do {
            _ = try await apiClient.exchangeLinkCode(code: "123456")
            XCTFail("Expected unauthorized")
        } catch APIError.unauthorized {
            // Expected.
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        XCTAssertEqual(sessionStore.patientToken, "existing-patient-token")
    }

    func testLinkExchangeMapsTransportFailureAndUsesShortTimeout() async throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(
            userDefaults: userDefaults,
            secureStorage: APIClientTestSecureStorage()
        )
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [APIClientMockURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        var receivedTimeout: TimeInterval?
        APIClientMockURLProtocol.requestHandler = { request in
            receivedTimeout = request.timeoutInterval
            throw URLError(.notConnectedToInternet)
        }
        let apiClient = APIClient(
            baseURL: URL(string: "http://localhost:3000")!,
            sessionStore: sessionStore,
            urlSession: urlSession,
            linkURLSession: urlSession
        )

        do {
            _ = try await apiClient.exchangeLinkCode(code: "123456")
            XCTFail("Expected network error")
        } catch APIError.network {
            // Expected.
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        XCTAssertEqual(receivedTimeout, 15)
    }

    func testLinkExchangeManualDeadlineStopsHangingTransport() async throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(
            userDefaults: userDefaults,
            secureStorage: APIClientTestSecureStorage()
        )
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [APIClientMockURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        APIClientMockURLProtocol.shouldHang = true
        let apiClient = APIClient(
            baseURL: URL(string: "http://localhost:3000")!,
            sessionStore: sessionStore,
            urlSession: urlSession,
            linkURLSession: urlSession,
            linkExchangeTimeout: 0.05
        )
        let startedAt = Date()

        do {
            _ = try await apiClient.exchangeLinkCode(code: "123456")
            XCTFail("Expected network timeout")
        } catch APIError.network {
            // Expected.
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        XCTAssertLessThan(Date().timeIntervalSince(startedAt), 1)
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

    func testDeleteCaregiverPrnDoseRecordUsesRecordEndpointAndAuthorization() async throws {
        let userDefaults = UserDefaults(suiteName: suiteName)!
        let sessionStore = SessionStore(
            userDefaults: userDefaults,
            secureStorage: APIClientTestSecureStorage()
        )
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverToken("caregiver-token")
        sessionStore.setCurrentPatientId("patient-1")
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [APIClientMockURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        var receivedRequest: URLRequest?
        APIClientMockURLProtocol.requestHandler = { request in
            receivedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 204,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }
        let apiClient = APIClient(
            baseURL: URL(string: "http://localhost:3000")!,
            sessionStore: sessionStore,
            urlSession: urlSession
        )

        try await apiClient.deleteCaregiverPrnDoseRecord(recordId: "prn-record-1")

        XCTAssertEqual(receivedRequest?.httpMethod, "DELETE")
        XCTAssertEqual(
            receivedRequest?.url?.path,
            "/api/patients/patient-1/prn-dose-records/prn-record-1"
        )
        XCTAssertEqual(receivedRequest?.value(forHTTPHeaderField: "Authorization"), "Bearer caregiver-token")
    }

    func testHistoryDayDecodesPrnRecordIdAndKeepsLegacyPayloadCompatible() throws {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let response = try decoder.decode(
            HistoryDayResponseDTO.self,
            from: Data(
                #"{"date":"2026-08-28","doses":[],"prnItems":[{"recordId":"prn-record-1","medicationId":"med-1","medicationName":"頓服A","takenAt":"2026-08-28T01:00:00Z","quantityTaken":1,"actorType":"CAREGIVER"},{"medicationId":"med-2","medicationName":"頓服B","takenAt":"2026-08-28T02:00:00Z","quantityTaken":2,"actorType":"PATIENT"}]}"#.utf8
            )
        )

        XCTAssertEqual(response.prnItems.first?.recordId, "prn-record-1")
        XCTAssertNil(response.prnItems.last?.recordId)
    }
}

private final class APIClientMockURLProtocol: URLProtocol {
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?
    nonisolated(unsafe) static var shouldHang = false

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        if Self.shouldHang { return }
        guard let handler = Self.requestHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

private final class APIClientTestSecureStorage: SessionSecureStorage {
    private var values: [String: String] = [:]

    func string(forKey key: String) -> String? { values[key] }

    func setString(_ value: String, forKey key: String) { values[key] = value }

    func removeString(forKey key: String) { values.removeValue(forKey: key) }
}
