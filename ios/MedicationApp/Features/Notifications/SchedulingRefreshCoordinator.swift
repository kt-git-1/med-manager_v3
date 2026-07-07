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
                let synced = await syncSlotTimesFromRegimens(
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

    /// Fetches today's schedule from the backend and derives slot times from regimen
    /// `scheduledAt` values. Updates the preferences store so that future notification
    /// scheduling uses the correct (caregiver-configured) times.
    private func syncSlotTimesFromRegimens(
        apiClient: APIClient,
        preferencesStore: NotificationPreferencesStore?
    ) async -> [NotificationSlot: (hour: Int, minute: Int)] {
        do {
            let doses = try await apiClient.fetchPatientToday()
            var slotTimeMap: [NotificationSlot: (hour: Int, minute: Int)] = [:]
            for dose in doses {
                guard let slot = NotificationSlot.from(date: dose.scheduledAt) else { continue }
                if slotTimeMap[slot] != nil { continue }
                var calendar = Calendar(identifier: .gregorian)
                calendar.timeZone = AppConstants.defaultTimeZone
                let components = calendar.dateComponents([.hour, .minute], from: dose.scheduledAt)
                guard let hour = components.hour, let minute = components.minute else { continue }
                slotTimeMap[slot] = (hour: hour, minute: minute)
            }
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
