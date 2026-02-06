import Foundation

extension APIClient {
    func fetchInventory(patientId: String? = nil) async throws -> [InventoryItemDTO] {
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        let url = baseURL.appendingPathComponent("api/patients/\(resolvedPatientId)/inventory")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        let payload = try decoder.decode(InventoryListResponseDTO.self, from: data)
        return payload.data.medications
    }

    func updateInventory(
        patientId: String? = nil,
        medicationId: String,
        input: InventoryUpdateRequestDTO
    ) async throws -> InventoryItemDTO {
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        let url = baseURL.appendingPathComponent(
            "api/patients/\(resolvedPatientId)/medications/\(medicationId)/inventory"
        )
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONEncoder().encode(input)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        return try decoder.decode(InventoryResponseDTO.self, from: data).data
    }

    func adjustInventory(
        patientId: String? = nil,
        medicationId: String,
        input: InventoryAdjustRequestDTO
    ) async throws -> InventoryItemDTO {
        let resolvedPatientId = try resolvedCaregiverPatientId(requestedPatientId: patientId)
        let url = baseURL.appendingPathComponent(
            "api/patients/\(resolvedPatientId)/medications/\(medicationId)/inventory/adjust"
        )
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = tokenForCurrentMode() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONEncoder().encode(input)
        let (data, response) = try await URLSession.shared.data(for: request)
        try mapErrorIfNeeded(response: response, data: data)
        let decoder = JSONDecoder()
        return try decoder.decode(InventoryResponseDTO.self, from: data).data
    }
}
