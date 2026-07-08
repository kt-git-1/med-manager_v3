import Foundation

struct PatientTodayNextSlotSelector {
    struct Candidate {
        let slot: NotificationSlot
        let scheduledAt: Date
        let remainingCount: Int
        let isWithinRecordingWindow: Bool
        let hasRecordableInventory: Bool
    }

    static func selectSlot(from candidates: [Candidate], now: Date = Date()) -> NotificationSlot? {
        candidates
            .filter { candidate in
                candidate.remainingCount > 0
                    && candidate.hasRecordableInventory
                    && (candidate.isWithinRecordingWindow || candidate.scheduledAt >= now)
            }
            .sorted { lhs, rhs in
                lhs.scheduledAt < rhs.scheduledAt
            }
            .first?
            .slot
    }
}
