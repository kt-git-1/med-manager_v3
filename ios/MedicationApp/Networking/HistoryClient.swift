import Foundation

@MainActor
final class HistoryClient {
    private let apiClient: APIClient
    private let calendar: Calendar
    private let dateFormatter: DateFormatter

    init(
        apiClient: APIClient,
        timeZone: TimeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current
    ) {
        self.apiClient = apiClient
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        self.calendar = calendar

        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        self.dateFormatter = formatter
    }

    func fetchMonthSummaries(
        windowStart: Date,
        days: Int,
        caregiverPatientId: String? = nil
    ) async throws -> [HistoryMonthResponseDTO] {
        let start = calendar.startOfDay(for: windowStart)
        let end = calendar.date(byAdding: .day, value: max(days - 1, 0), to: start) ?? start

        let startComponents = calendar.dateComponents([.year, .month], from: start)
        let endComponents = calendar.dateComponents([.year, .month], from: end)

        guard let startYear = startComponents.year,
              let startMonth = startComponents.month,
              let endYear = endComponents.year,
              let endMonth = endComponents.month else {
            return []
        }

        if let caregiverPatientId {
            if startYear == endYear && startMonth == endMonth {
                let month = try await apiClient.fetchCaregiverHistoryMonth(
                    patientId: caregiverPatientId,
                    year: startYear,
                    month: startMonth
                )
                return [month]
            }

            let currentMonth = try await apiClient.fetchCaregiverHistoryMonth(
                patientId: caregiverPatientId,
                year: startYear,
                month: startMonth
            )
            let nextMonth = try await apiClient.fetchCaregiverHistoryMonth(
                patientId: caregiverPatientId,
                year: endYear,
                month: endMonth
            )
            return [currentMonth, nextMonth]
        }

        if startYear == endYear && startMonth == endMonth {
            let month = try await apiClient.fetchPatientHistoryMonth(year: startYear, month: startMonth)
            return [month]
        }

        let currentMonth = try await apiClient.fetchPatientHistoryMonth(year: startYear, month: startMonth)
        let nextMonth = try await apiClient.fetchPatientHistoryMonth(year: endYear, month: endMonth)
        return [currentMonth, nextMonth]
    }

    func fetchDayDetail(date: Date) async throws -> HistoryDayResponseDTO {
        let dateKey = dateFormatter.string(from: date)
        return try await apiClient.fetchPatientHistoryDay(date: dateKey)
    }
}
