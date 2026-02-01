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

    func fetchMedications(patientId: String) async throws -> [MedicationDTO] {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/medications"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "patientId", value: patientId)]
        guard let url = components?.url else {
            throw APIError.unknown
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MedicationListResponseDTO.self, from: data).data
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
        switch httpResponse.statusCode {
        case 200...299:
            return
        case 401:
            throw APIError.unauthorized
        case 403:
            throw APIError.forbidden
        case 404:
            throw APIError.notFound
        case 409:
            throw APIError.conflict
        case 422:
            let message = String(data: data, encoding: .utf8) ?? "validation error"
            throw APIError.validation(message)
        default:
            throw APIError.unknown
        }
    }
}
