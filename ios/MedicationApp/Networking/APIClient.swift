import Foundation

@MainActor
final class APIClient {
    let baseURL: URL
    let sessionStore: SessionStore

    init(baseURL: URL, sessionStore: SessionStore) {
        self.baseURL = baseURL
        self.sessionStore = sessionStore
    }

    func request(path: String, method: String = "GET") async throws -> Data {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = method
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        return data
    }

    func listPatients() async throws -> [PatientDTO] {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        return try decoder.decode(PatientListResponseDTO.self, from: data).data
    }

    func createPatient(displayName: String) async throws -> PatientDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let payload = ["displayName": displayName]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        let data = try await send(request)
        let decoder = JSONDecoder()
        return try decoder.decode(CreatePatientResponseDTO.self, from: data).data
    }

    func updatePatientSlotTimes(patientId: String, slotTimes: PatientSlotTimesDTO) async throws -> PatientSlotTimesDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)")
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONEncoder().encode(PatientSlotTimesUpdateRequestDTO(slotTimes: slotTimes))
        let data = try await send(request)
        return try JSONDecoder().decode(PatientSlotTimesResponseDTO.self, from: data).data.slotTimes
    }

    func fetchPatientSlotTimes() async throws -> PatientSlotTimesDTO {
        let url = baseURL.appendingPathComponent("api/patient/slot-times")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        return try JSONDecoder().decode(PatientSlotTimesResponseDTO.self, from: data).data.slotTimes
    }

    func issueLinkingCode(patientId: String) async throws -> LinkingCodeDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)/linking-codes")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(LinkingCodeResponseDTO.self, from: data).data
    }

    func revokePatient(patientId: String) async throws {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)/revoke")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        _ = try decoder.decode(RevokeResponseDTO.self, from: data)
        sessionStore.handlePatientRevoked(patientId)
    }

    func deletePatient(patientId: String) async throws {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)")
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        _ = try decoder.decode(DeletePatientResponseDTO.self, from: data)
        sessionStore.handlePatientRevoked(patientId)
    }

    func deleteCaregiverAccount() async throws {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/me")
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        _ = try decoder.decode(DeletePatientResponseDTO.self, from: data)
    }

    func fetchMedications(patientId: String?) async throws -> [MedicationDTO] {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeMedicationListRequest(patientId: patientId)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationListResponseDTO.self, from: data).data
    }

    func createMedication(_ input: MedicationCreateRequestDTO) async throws -> MedicationDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeMedicationCreateRequest(input: input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationResponseDTO.self, from: data).data
    }

    func updateMedication(id: String, patientId: String, input: MedicationUpdateRequestDTO) async throws -> MedicationDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeMedicationUpdateRequest(id: id, patientId: patientId, input: input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationResponseDTO.self, from: data).data
    }

    func deleteMedication(id: String, patientId: String) async throws {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeMedicationDeleteRequest(id: id, patientId: patientId)
        _ = try await send(request)
    }

    func fetchRegimens(medicationId: String) async throws -> [RegimenDTO] {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeRegimenListRequest(medicationId: medicationId)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RegimenListResponseDTO.self, from: data).data
    }

    func createRegimen(medicationId: String, input: RegimenCreateRequestDTO) async throws -> RegimenDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeRegimenCreateRequest(medicationId: medicationId, input: input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RegimenResponseDTO.self, from: data).data
    }

    func updateRegimen(id: String, input: RegimenUpdateRequestDTO) async throws -> RegimenDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let request = try makeRegimenUpdateRequest(id: id, input: input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RegimenResponseDTO.self, from: data).data
    }

    func exchangeLinkCode(code: String) async throws -> PatientSessionTokenDTO {
        let url = baseURL.appendingPathComponent("api/patient/link")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        let payload = ["code": code]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        do {
            let result = try decoder.decode(PatientSessionResponseDTO.self, from: data)
            return result.data
        } catch {
            throw APIError.network("Invalid server response")
        }
    }

    func refreshPatientSessionToken() async throws -> PatientSessionTokenDTO {
        let url = baseURL.appendingPathComponent("api/patient/session/refresh")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request, allowsPatientRefreshRetry: false)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let result = try decoder.decode(PatientSessionResponseDTO.self, from: data)
        return result.data
    }

    func revokePatientSession() async throws {
        let url = baseURL.appendingPathComponent("api/patient/session")
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        _ = try await send(request, allowsPatientRefreshRetry: false)
    }

    func fetchPatientToday(slotTimeItems: [URLQueryItem] = []) async throws -> [ScheduleDoseDTO] {
        var url = baseURL.appendingPathComponent("api/patient/today")
        if !slotTimeItems.isEmpty {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            components?.queryItems = slotTimeItems
            url = components?.url ?? url
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(ScheduleResponseDTO.self, from: data).data
    }

    func fetchPatientHistoryMonth(
        year: Int,
        month: Int,
        slotTimeItems: [URLQueryItem] = []
    ) async throws -> HistoryMonthResponseDTO {
        var queryItems = [
            URLQueryItem(name: "year", value: String(year)),
            URLQueryItem(name: "month", value: String(month))
        ]
        queryItems.append(contentsOf: slotTimeItems)
        let request = try makeHistoryRequest(
            path: "api/patient/history/month",
            queryItems: queryItems
        )
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(HistoryMonthResponseDTO.self, from: data)
    }

    func fetchPatientHistoryDay(
        date: String,
        slotTimeItems: [URLQueryItem] = []
    ) async throws -> HistoryDayResponseDTO {
        var queryItems = [URLQueryItem(name: "date", value: date)]
        queryItems.append(contentsOf: slotTimeItems)
        let request = try makeHistoryRequest(
            path: "api/patient/history/day",
            queryItems: queryItems
        )
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(HistoryDayResponseDTO.self, from: data)
    }

    func createPatientDoseRecord(input: DoseRecordCreateRequestDTO) async throws -> DoseRecordDTO {
        let url = baseURL.appendingPathComponent("api/patient/dose-records")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        request.httpBody = try encoder.encode(input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(DoseRecordResponseDTO.self, from: data).data
    }

    func bulkRecordSlot(date: String, slot: String, slotTimes: [URLQueryItem]) async throws -> SlotBulkRecordResponseDTO {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/patient/dose-records/slot"), resolvingAgainstBaseURL: false)!
        if !slotTimes.isEmpty {
            components.queryItems = slotTimes
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(SlotBulkRecordRequestDTO(date: date, slot: slot))
        let data = try await send(request)
        let decoder = JSONDecoder()
        return try decoder.decode(SlotBulkRecordResponseDTO.self, from: data)
    }

    func createPrnDoseRecord(
        patientId: String,
        input: PrnDoseRecordCreateRequestDTO
    ) async throws -> PrnDoseRecordDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)/prn-dose-records")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        request.httpBody = try encoder.encode(input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(PrnDoseRecordCreateResponseDTO.self, from: data).record
    }

    func fetchCaregiverToday(
        patientId: String? = nil,
        slotTimeItems: [URLQueryItem] = []
    ) async throws -> [ScheduleDoseDTO] {
        await refreshCaregiverAuthenticationIfNeeded()
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        var url = baseURL.appendingPathComponent("api/patients/\(resolvedPatientId)/today")
        if !slotTimeItems.isEmpty {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            components?.queryItems = slotTimeItems
            url = components?.url ?? url
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(ScheduleResponseDTO.self, from: data).data
    }

    func fetchCaregiverHistoryMonth(
        patientId: String? = nil,
        year: Int,
        month: Int,
        slotTimeItems: [URLQueryItem] = []
    ) async throws -> HistoryMonthResponseDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        var queryItems = [
            URLQueryItem(name: "year", value: String(year)),
            URLQueryItem(name: "month", value: String(month))
        ]
        queryItems.append(contentsOf: slotTimeItems)
        let request = try makeHistoryRequest(
            path: "api/patients/\(resolvedPatientId)/history/month",
            queryItems: queryItems
        )
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(HistoryMonthResponseDTO.self, from: data)
    }

    func fetchCaregiverHistoryDay(
        patientId: String? = nil,
        date: String,
        slotTimeItems: [URLQueryItem] = []
    ) async throws -> HistoryDayResponseDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        var queryItems = [URLQueryItem(name: "date", value: date)]
        queryItems.append(contentsOf: slotTimeItems)
        let request = try makeHistoryRequest(
            path: "api/patients/\(resolvedPatientId)/history/day",
            queryItems: queryItems
        )
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(HistoryDayResponseDTO.self, from: data)
    }

    // MARK: - History Report (011-pdf-export)

    func fetchCaregiverHistoryReport(
        patientId: String? = nil,
        from: String,
        to: String
    ) async throws -> HistoryReportResponseDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        let queryItems = [
            URLQueryItem(name: "from", value: from),
            URLQueryItem(name: "to", value: to),
        ]
        let request = try makeHistoryRequest(
            path: "api/patients/\(resolvedPatientId)/history/report",
            queryItems: queryItems
        )
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(HistoryReportResponseDTO.self, from: data)
    }

    func createCaregiverDoseRecord(
        patientId: String? = nil,
        input: DoseRecordCreateRequestDTO
    ) async throws -> DoseRecordDTO {
        await refreshCaregiverAuthenticationIfNeeded()
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        let url = baseURL.appendingPathComponent("api/patients/\(resolvedPatientId)/dose-records")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        request.httpBody = try encoder.encode(input)
        let data = try await send(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(DoseRecordResponseDTO.self, from: data).data
    }

    func deleteCaregiverDoseRecord(
        patientId: String? = nil,
        medicationId: String,
        scheduledAt: Date
    ) async throws {
        await refreshCaregiverAuthenticationIfNeeded()
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        var components = URLComponents(
            url: baseURL.appendingPathComponent("api/patients/\(resolvedPatientId)/dose-records"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "medicationId", value: medicationId),
            URLQueryItem(name: "scheduledAt", value: iso8601String(from: scheduledAt))
        ]
        guard let url = components?.url else {
            throw APIError.unknown
        }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        _ = try await send(request)
    }

    @MainActor
    func refreshCaregiverAuthenticationIfNeeded() async {
        guard sessionStore.mode == .caregiver else { return }
        await sessionStore.refreshCaregiverTokenIfNeeded()
    }

    @MainActor
    func refreshPatientAuthenticationIfNeeded() async throws {
        guard sessionStore.shouldRefreshPatientToken() else { return }
        let refreshedSession = try await refreshPatientSessionToken()
        sessionStore.savePatientToken(
            refreshedSession.patientSessionToken,
            expiresAt: refreshedSession.expiresAt
        )
    }

    func send(
        _ request: URLRequest,
        allowsPatientRefreshRetry: Bool = true
    ) async throws -> Data {
        await refreshCaregiverAuthenticationIfNeeded()
        if allowsPatientRefreshRetry {
            do {
                try await refreshPatientAuthenticationIfNeeded()
            } catch {
                if !sessionStore.isPatientTutorialPreviewActive {
                    sessionStore.handleAuthFailure(for: .patient)
                }
                throw error
            }
        }
        let authorizedRequest = requestWithCurrentAuthorization(request)
        let (data, response) = try await URLSession.shared.data(for: authorizedRequest)
        if shouldRefreshPatientSession(
            response: response,
            allowsPatientRefreshRetry: allowsPatientRefreshRetry
        ) {
            do {
                let refreshedSession = try await refreshPatientSessionToken()
                sessionStore.savePatientToken(
                    refreshedSession.patientSessionToken,
                    expiresAt: refreshedSession.expiresAt
                )
                let retryRequest = requestWithCurrentAuthorization(request)
                let (retryData, retryResponse) = try await URLSession.shared.data(for: retryRequest)
                try mapErrorIfNeeded(response: retryResponse, data: retryData)
                return retryData
            } catch {
                if !sessionStore.isPatientTutorialPreviewActive {
                    sessionStore.handleAuthFailure(for: .patient)
                }
                throw error
            }
        }
        try mapErrorIfNeeded(response: response, data: data)
        return data
    }

    private func requestWithCurrentAuthorization(_ request: URLRequest) -> URLRequest {
        var request = request
        request.setValue(nil, forHTTPHeaderField: "Authorization")
        if let token = tokenForCurrentMode() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func shouldRefreshPatientSession(
        response: URLResponse,
        allowsPatientRefreshRetry: Bool
    ) -> Bool {
        guard allowsPatientRefreshRetry,
              sessionStore.mode == .patient,
              sessionStore.patientToken != nil,
              !sessionStore.isPatientTutorialPreviewActive,
              let httpResponse = response as? HTTPURLResponse else {
            return false
        }
        return httpResponse.statusCode == 401
    }

    @MainActor
    func tokenForCurrentMode() -> String? {
        switch sessionStore.mode {
        case .caregiver:
            return sessionStore.caregiverToken
        case .patient:
            return sessionStore.patientToken
        case .none:
            return nil
        }
    }

    @MainActor
    func mapErrorIfNeeded(response: URLResponse, data: Data) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            return
        }
        let message = parseErrorMessage(from: data)
        switch httpResponse.statusCode {
        case 200...299:
            return
        case 401:
            if !sessionStore.isPatientTutorialPreviewActive {
                sessionStore.handleAuthFailure(for: sessionStore.mode)
            }
            throw APIError.unauthorized
        case 403:
            // Check for PATIENT_LIMIT_EXCEEDED before generic 403 handling.
            if let limitError = parsePatientLimitExceeded(from: data) {
                throw limitError
            }
            // Check for HISTORY_RETENTION_LIMIT before generic 403 handling.
            // Retention errors must not clear the session.
            if let retentionError = parseHistoryRetentionLimit(from: data) {
                throw retentionError
            }
            throw APIError.forbidden
        case 400:
            throw APIError.validation(message ?? "Bad request")
        case 404:
            throw APIError.notFound
        case 409:
            if parseErrorCode(from: data) == "insufficient_inventory" {
                throw APIError.insufficientInventory
            }
            throw APIError.conflict
        case 422:
            throw APIError.validation(message ?? "validation error")
        default:
            throw APIError.network(message ?? "Request failed")
        }
    }

    private func parseErrorMessage(from data: Data) -> String? {
        struct ErrorPayload: Decodable {
            let error: String?
            let message: String?
            let messages: [String]?
        }
        if let payload = try? JSONDecoder().decode(ErrorPayload.self, from: data) {
            if let messages = payload.messages, !messages.isEmpty {
                return messages.joined(separator: "\n")
            }
            return payload.message ?? payload.error
        }
        if let text = String(data: data, encoding: .utf8), !text.isEmpty {
            return text
        }
        return nil
    }

    private func parseErrorCode(from data: Data) -> String? {
        struct ErrorPayload: Decodable {
            let error: String?
        }
        return try? JSONDecoder().decode(ErrorPayload.self, from: data).error
    }

    /// Parses the PATIENT_LIMIT_EXCEEDED error response from a 403 body.
    /// Returns `APIError.patientLimitExceeded` if the response matches the contract,
    /// or `nil` if it's a generic 403 (auth failure).
    private func parsePatientLimitExceeded(from data: Data) -> APIError? {
        struct LimitPayload: Decodable {
            let code: String
            let limit: Int
            let current: Int
        }
        guard let payload = try? JSONDecoder().decode(LimitPayload.self, from: data),
              payload.code == "PATIENT_LIMIT_EXCEEDED" else {
            return nil
        }
        return .patientLimitExceeded(limit: payload.limit, current: payload.current)
    }

    /// Parses the HISTORY_RETENTION_LIMIT error response from a 403 body.
    /// Returns `APIError.historyRetentionLimit` if the response matches the contract,
    /// or `nil` if it's not a retention error.
    private func parseHistoryRetentionLimit(from data: Data) -> APIError? {
        struct RetentionPayload: Decodable {
            let code: String
            let cutoffDate: String
            let retentionDays: Int
        }
        guard let payload = try? JSONDecoder().decode(RetentionPayload.self, from: data),
              payload.code == "HISTORY_RETENTION_LIMIT" else {
            return nil
        }
        return .historyRetentionLimit(cutoffDate: payload.cutoffDate, retentionDays: payload.retentionDays)
    }

    @MainActor
    func makeMedicationListRequest(patientId: String?) throws -> URLRequest {
        let resolvedPatientId = try resolvedMedicationPatientId(requestedPatientId: patientId)
        let url = try makeMedicationURL(path: "api/medications", patientId: resolvedPatientId)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    @MainActor
    func makeMedicationCreateRequest(input: MedicationCreateRequestDTO) throws -> URLRequest {
        let resolvedInput = try resolvedMedicationCreateInput(input)
        let url = baseURL.appendingPathComponent("api/medications")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try medicationEncoder().encode(resolvedInput)
        return request
    }

    @MainActor
    func makeMedicationUpdateRequest(
        id: String,
        patientId: String,
        input: MedicationUpdateRequestDTO
    ) throws -> URLRequest {
        let resolvedPatientId = try resolvedMedicationPatientId(requestedPatientId: patientId)
        let url = try makeMedicationURL(path: "api/medications/\(id)", patientId: resolvedPatientId)
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try medicationEncoder().encode(input)
        return request
    }

    @MainActor
    func makeMedicationDeleteRequest(id: String, patientId: String) throws -> URLRequest {
        let resolvedPatientId = try resolvedMedicationPatientId(requestedPatientId: patientId)
        let url = try makeMedicationURL(path: "api/medications/\(id)", patientId: resolvedPatientId)
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    @MainActor
    func makeRegimenListRequest(medicationId: String) throws -> URLRequest {
        let url = baseURL.appendingPathComponent("api/medications/\(medicationId)/regimens")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    @MainActor
    func makeRegimenCreateRequest(
        medicationId: String,
        input: RegimenCreateRequestDTO
    ) throws -> URLRequest {
        let url = baseURL.appendingPathComponent("api/medications/\(medicationId)/regimens")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try regimenEncoder().encode(input)
        return request
    }

    @MainActor
    func makeRegimenUpdateRequest(
        id: String,
        input: RegimenUpdateRequestDTO
    ) throws -> URLRequest {
        let url = baseURL.appendingPathComponent("api/regimens/\(id)")
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try regimenEncoder().encode(input)
        return request
    }

    @MainActor
    private func resolvedMedicationPatientId(requestedPatientId: String?) throws -> String? {
        if sessionStore.mode == .caregiver {
            guard let patientId = sessionStore.currentPatientId, !patientId.isEmpty else {
                throw APIError.validation("patientId required")
            }
            return patientId
        }
        return requestedPatientId
    }

    @MainActor
    private func resolvedMedicationCreateInput(
        _ input: MedicationCreateRequestDTO
    ) throws -> MedicationCreateRequestDTO {
        if sessionStore.mode == .caregiver {
            guard let patientId = sessionStore.currentPatientId, !patientId.isEmpty else {
                throw APIError.validation("patientId required")
            }
            return MedicationCreateRequestDTO(
                patientId: patientId,
                name: input.name,
                dosageText: input.dosageText,
                doseCountPerIntake: input.doseCountPerIntake,
                dosageStrengthValue: input.dosageStrengthValue,
                dosageStrengthUnit: input.dosageStrengthUnit,
                notes: input.notes,
                isPrn: input.isPrn,
                prnInstructions: input.prnInstructions,
                startDate: input.startDate,
                endDate: input.endDate,
                inventoryCount: input.inventoryCount,
                inventoryUnit: input.inventoryUnit
            )
        }
        if input.patientId.isEmpty {
            throw APIError.validation("patientId required")
        }
        return input
    }

    @MainActor
    func resolvedCaregiverPatientId(requestedPatientId: String?) throws -> String {
        if let requestedPatientId, !requestedPatientId.isEmpty {
            return requestedPatientId
        }
        guard sessionStore.mode == .caregiver,
              let patientId = sessionStore.currentPatientId,
              !patientId.isEmpty else {
            throw APIError.validation("patientId required")
        }
        return patientId
    }

    private func iso8601String(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private func makeHistoryRequest(path: String, queryItems: [URLQueryItem]) throws -> URLRequest {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)
        components?.queryItems = queryItems
        guard let url = components?.url else {
            throw APIError.unknown
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func makeMedicationURL(path: String, patientId: String?) throws -> URL {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)
        if let patientId, !patientId.isEmpty {
            components?.queryItems = [URLQueryItem(name: "patientId", value: patientId)]
        }
        guard let url = components?.url else {
            throw APIError.unknown
        }
        return url
    }

    // MARK: - Entitlements (Billing)

    func claimEntitlement(_ request: ClaimRequest) async throws -> ClaimResponse {
        let url = baseURL.appendingPathComponent("api/iap/claim")
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            urlRequest.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let encoder = JSONEncoder()
        urlRequest.httpBody = try encoder.encode(request)
        let data = try await send(urlRequest)
        let decoder = JSONDecoder()
        return try decoder.decode(ClaimResponse.self, from: data)
    }

    func getEntitlements() async throws -> EntitlementsResponse {
        let url = baseURL.appendingPathComponent("api/me/entitlements")
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            urlRequest.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let data = try await send(urlRequest)
        let decoder = JSONDecoder()
        return try decoder.decode(EntitlementsResponse.self, from: data)
    }

    // MARK: - Device Tokens (Push Notifications)

    func registerDeviceToken(token: String, platform: String = "ios") async throws {
        let url = baseURL.appendingPathComponent("api/device-tokens")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken = tokenForCurrentMode() {
            request.addValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        let payload: [String: String] = ["token": token, "platform": platform]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await send(request)
    }

    func unregisterDeviceToken(token: String) async throws {
        let url = baseURL.appendingPathComponent("api/device-tokens")
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken = tokenForCurrentMode() {
            request.addValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        let payload: [String: String] = ["token": token]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await send(request)
    }

    // MARK: - Push Device Registration (FCM, 012-push-foundation)

    /// Register an FCM push device token with the backend.
    /// POST /api/push/register { token, platform, environment }
    func registerPushDevice(token: String, platform: String = "ios", environment: String) async throws {
        let url = baseURL.appendingPathComponent("api/push/register")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken = tokenForCurrentMode() {
            request.addValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        let payload: [String: String] = [
            "token": token,
            "platform": platform,
            "environment": environment
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await send(request)
    }

    /// Unregister an FCM push device token from the backend.
    /// POST /api/push/unregister { token }
    func unregisterPushDevice(token: String) async throws {
        let url = baseURL.appendingPathComponent("api/push/unregister")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken = tokenForCurrentMode() {
            request.addValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        let payload: [String: String] = ["token": token]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await send(request)
    }

    // MARK: - Private Helpers

    private func medicationEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    private func regimenEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }
}
