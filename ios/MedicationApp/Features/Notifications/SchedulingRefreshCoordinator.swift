import Foundation

@MainActor
final class SchedulingRefreshCoordinator: ObservableObject {
    enum RefreshTrigger {
        case appLaunch
        case appForeground
        case settingsChange
        case doseRecorded
    }

    @Published private(set) var isRefreshing: Bool = false
    @Published private(set) var errorMessage: String?

    private let planBuilder: NotificationPlanBuilder
    private let scheduler: NotificationScheduler

    init(
        planBuilder: NotificationPlanBuilder = NotificationPlanBuilder(),
        scheduler: NotificationScheduler = NotificationScheduler()
    ) {
        self.planBuilder = planBuilder
        self.scheduler = scheduler
    }

    func refresh(
        apiClient: APIClient,
        includeSecondary: Bool,
        enabledSlots: Set<NotificationSlot> = Set(NotificationSlot.allCases),
        slotTimes: [NotificationSlot: (hour: Int, minute: Int)] = [:],
        caregiverPatientId: String? = nil,
        preferencesStore: NotificationPreferencesStore? = nil,
        trigger: RefreshTrigger,
        now: Date = Date()
    ) async {
        guard !isRefreshing else { return }
        isRefreshing = true
        errorMessage = nil

        do {
            var effectiveSlotTimes = slotTimes

            if caregiverPatientId == nil && effectiveSlotTimes.isEmpty {
                let synced = await syncPatientSlotTimes(
                    apiClient: apiClient,
                    preferencesStore: preferencesStore
                )
                if !synced.isEmpty {
                    effectiveSlotTimes = synced
                }
            }

            let slotTimeItems = Self.slotTimeQueryItems(from: effectiveSlotTimes)
            let historyClient = HistoryClient(apiClient: apiClient)
            let monthSummaries = try await historyClient.fetchMonthSummaries(
                windowStart: now,
                days: 7,
                caregiverPatientId: caregiverPatientId,
                slotTimeItems: slotTimeItems
            )
            let plan = planBuilder.buildPlan(
                monthSummaries: monthSummaries,
                includeSecondary: includeSecondary,
                enabledSlots: enabledSlots,
                slotTimes: effectiveSlotTimes,
                now: now
            )
            await scheduler.schedule(planEntries: plan, now: now)
        } catch {
            errorMessage = error.localizedDescription
        }

        isRefreshing = false
    }

    func cancelScheduledNotifications() async {
        await scheduler.cancelAllScheduledNotifications()
    }

    /// Fetches patient-scoped slot times directly. This avoids deriving notification
    /// times from today's schedule, which can be empty for weekly/alternate-day meds.
    private func syncPatientSlotTimes(
        apiClient: APIClient,
        preferencesStore: NotificationPreferencesStore?
    ) async -> [NotificationSlot: (hour: Int, minute: Int)] {
        do {
            let dto = try await apiClient.fetchPatientSlotTimes()
            let slotTimeMap = Self.slotTimesMap(from: dto)
            if let store = preferencesStore {
                for (slot, time) in slotTimeMap {
                    let current = store.slotTime(for: slot)
                    if current.hour != time.hour || current.minute != time.minute {
                        store.setSlotTime(slot, hour: time.hour, minute: time.minute)
                    }
                }
            }
            return slotTimeMap
        } catch {
            return [:]
        }
    }

    private static func slotTimesMap(from dto: PatientSlotTimesDTO) -> [NotificationSlot: (hour: Int, minute: Int)] {
        [
            .morning: parseTime(dto.morning) ?? NotificationSlot.morning.hourMinute,
            .noon: parseTime(dto.noon) ?? NotificationSlot.noon.hourMinute,
            .evening: parseTime(dto.evening) ?? NotificationSlot.evening.hourMinute,
            .bedtime: parseTime(dto.bedtime) ?? NotificationSlot.bedtime.hourMinute
        ]
    }

    private static func parseTime(_ value: String) -> (hour: Int, minute: Int)? {
        let parts = value.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2,
              (0...23).contains(parts[0]),
              (0...59).contains(parts[1]) else {
            return nil
        }
        return (parts[0], parts[1])
    }

    static func slotTimeQueryItems(
        from slotTimes: [NotificationSlot: (hour: Int, minute: Int)]
    ) -> [URLQueryItem] {
        guard !slotTimes.isEmpty else { return [] }
        return NotificationSlot.allCases.compactMap { slot in
            guard let time = slotTimes[slot] else { return nil }
            let value = String(format: "%02d:%02d", time.hour, time.minute)
            return URLQueryItem(name: "\(slot.rawValue)Time", value: value)
        }
    }
}
