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
        trigger: RefreshTrigger,
        now: Date = Date()
    ) async {
        guard !isRefreshing else { return }
        isRefreshing = true
        errorMessage = nil

        do {
            let historyClient = HistoryClient(apiClient: apiClient)
            let monthSummaries = try await historyClient.fetchMonthSummaries(
                windowStart: now,
                days: 7,
                caregiverPatientId: caregiverPatientId
            )
            let plan = planBuilder.buildPlan(
                monthSummaries: monthSummaries,
                includeSecondary: includeSecondary,
                enabledSlots: enabledSlots,
                slotTimes: slotTimes,
                now: now
            )
            await scheduler.schedule(planEntries: plan, now: now)
        } catch {
            errorMessage = error.localizedDescription
        }

        isRefreshing = false
    }
}
