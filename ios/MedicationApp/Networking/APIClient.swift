import Foundation

final class APIClient {
    private let baseURL: URL
    private let sessionStore: SessionStore

    init(baseURL: URL, sessionStore: SessionStore) {
        self.baseURL = baseURL
        self.sessionStore = sessionStore
    }

    func request(path: String, method: String = "GET") async throws -> Data {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = method
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        return data
    }

    func listPatients() async throws -> [PatientDTO] {
        let url = baseURL.appendingPathComponent("api/patients")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        return try decoder.decode(PatientListResponseDTO.self, from: data).data
    }

    func createPatient(displayName: String) async throws -> PatientDTO {
        let url = baseURL.appendingPathComponent("api/patients")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let payload = ["displayName": displayName]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        return try decoder.decode(CreatePatientResponseDTO.self, from: data).data
    }

    func issueLinkingCode(patientId: String) async throws -> LinkingCodeDTO {
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)/linking-codes")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(LinkingCodeResponseDTO.self, from: data).data
    }

    func revokePatient(patientId: String) async throws {
        let url = baseURL.appendingPathComponent("api/patients/\(patientId)/revoke")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        _ = try decoder.decode(RevokeResponseDTO.self, from: data)
        sessionStore.handlePatientRevoked(patientId)
    }

    func fetchMedications(patientId: String?) async throws -> [MedicationDTO] {
        let request = try makeMedicationListRequest(patientId: patientId)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationListResponseDTO.self, from: data).data
    }

    func createMedication(_ input: MedicationCreateRequestDTO) async throws -> MedicationDTO {
        let request = try makeMedicationCreateRequest(input: input)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationResponseDTO.self, from: data).data
    }

    func updateMedication(id: String, patientId: String, input: MedicationUpdateRequestDTO) async throws -> MedicationDTO {
        let request = try makeMedicationUpdateRequest(id: id, patientId: patientId, input: input)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationResponseDTO.self, from: data).data
    }

    func deleteMedication(id: String, patientId: String) async throws {
        let request = try makeMedicationDeleteRequest(id: id, patientId: patientId)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
    }

    func fetchRegimens(medicationId: String) async throws -> [RegimenDTO] {
        let request = try makeRegimenListRequest(medicationId: medicationId)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RegimenListResponseDTO.self, from: data).data
    }

    func createRegimen(medicationId: String, input: RegimenCreateRequestDTO) async throws -> RegimenDTO {
        let request = try makeRegimenCreateRequest(medicationId: medicationId, input: input)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RegimenResponseDTO.self, from: data).data
    }

    func updateRegimen(id: String, input: RegimenUpdateRequestDTO) async throws -> RegimenDTO {
        let request = try makeRegimenUpdateRequest(id: id, input: input)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RegimenResponseDTO.self, from: data).data
    }

    func exchangeLinkCode(code: String) async throws -> String {
        let url = baseURL.appendingPathComponent("api/patient/link")
        print("APIClient: exchangeLinkCode url=\(url.absoluteString)")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        let payload = ["code": code]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        do {
            let result = try decoder.decode(PatientSessionResponseDTO.self, from: data)
            return result.data.patientSessionToken
        } catch {
            throw APIError.network("Invalid server response")
        }
    }

    func refreshPatientSessionToken() async throws -> String {
        let url = baseURL.appendingPathComponent("api/patient/session/refresh")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        let result = try decoder.decode(PatientSessionResponseDTO.self, from: data)
        return result.data.patientSessionToken
    }

    private func tokenForCurrentMode() -> String? {
        switch sessionStore.mode {
        case .caregiver:
            return sessionStore.caregiverToken
        case .patient:
            return sessionStore.patientToken
        case .none:
            return nil
        }
    }

    private func mapErrorIfNeeded(response: URLResponse, data: Data) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            return
        }
        let message = parseErrorMessage(from: data)
        switch httpResponse.statusCode {
        case 200...299:
            return
        case 401:
            sessionStore.handleAuthFailure(for: sessionStore.mode)
            throw APIError.unauthorized
        case 403:
            sessionStore.handleAuthFailure(for: sessionStore.mode)
            throw APIError.forbidden
        case 404:
            throw APIError.notFound
        case 409:
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

    func makeRegimenListRequest(medicationId: String) throws -> URLRequest {
        let url = baseURL.appendingPathComponent("api/medications/\(medicationId)/regimens")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

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

    private func resolvedMedicationPatientId(requestedPatientId: String?) throws -> String? {
        if sessionStore.mode == .caregiver {
            guard let patientId = sessionStore.currentPatientId, !patientId.isEmpty else {
                throw APIError.validation("patientId required")
            }
            return patientId
        }
        return requestedPatientId
    }

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
