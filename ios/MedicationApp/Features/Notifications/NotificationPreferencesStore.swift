import Foundation

extension Notification.Name {
    static let presetTimesUpdated = Notification.Name("presetTimesUpdated")
    static let medicationUpdated = Notification.Name("medicationUpdated")
    static let authFailure = Notification.Name("authFailure")
}

@MainActor
final class NotificationPreferencesStore: ObservableObject {
    @Published var masterEnabled: Bool {
        didSet {
            defaults.set(masterEnabled, forKey: masterKey)
        }
    }

    @Published var rereminderEnabled: Bool {
        didSet {
            defaults.set(rereminderEnabled, forKey: rereminderKey)
        }
    }

    @Published private var slotEnabled: [NotificationSlot: Bool]
    @Published private var slotTimes: [NotificationSlot: DateComponents]

    private let defaults: UserDefaults
    private let masterKey = "notif.masterEnabled"
    private let rereminderKey = "notif.rereminderEnabled"
    private let slotKeyPrefix = "notif.slot."
    private let slotTimeKeyPrefix = "notif.slotTime."

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.masterEnabled = defaults.object(forKey: masterKey) as? Bool ?? false
        self.rereminderEnabled = defaults.object(forKey: rereminderKey) as? Bool ?? false

        var slots: [NotificationSlot: Bool] = [:]
        for slot in NotificationSlot.allCases {
            let key = slotKeyPrefix + slot.rawValue
            slots[slot] = defaults.object(forKey: key) as? Bool ?? true
        }
        self.slotEnabled = slots

        var times: [NotificationSlot: DateComponents] = [:]
        for slot in NotificationSlot.allCases {
            let key = slotTimeKeyPrefix + slot.rawValue
            let value = defaults.string(forKey: key)
            if let parsed = Self.parseTimeString(value) {
                times[slot] = parsed
            } else {
                times[slot] = DateComponents(
                    hour: slot.hourMinute.hour,
                    minute: slot.hourMinute.minute
                )
            }
        }
        self.slotTimes = times
    }

    func isSlotEnabled(_ slot: NotificationSlot) -> Bool {
        slotEnabled[slot] ?? true
    }

    func setSlotEnabled(_ slot: NotificationSlot, enabled: Bool) {
        slotEnabled[slot] = enabled
        defaults.set(enabled, forKey: slotKeyPrefix + slot.rawValue)
    }

    func enabledSlots() -> Set<NotificationSlot> {
        Set(NotificationSlot.allCases.filter { isSlotEnabled($0) })
    }

    func slotTime(for slot: NotificationSlot) -> (hour: Int, minute: Int) {
        let components = slotTimes[slot]
        return (
            hour: components?.hour ?? slot.hourMinute.hour,
            minute: components?.minute ?? slot.hourMinute.minute
        )
    }

    func slotTimesMap() -> [NotificationSlot: (hour: Int, minute: Int)] {
        var result: [NotificationSlot: (hour: Int, minute: Int)] = [:]
        for slot in NotificationSlot.allCases {
            result[slot] = slotTime(for: slot)
        }
        return result
    }

    func setSlotTime(_ slot: NotificationSlot, hour: Int, minute: Int) {
        let sanitizedHour = min(max(hour, 0), 23)
        let sanitizedMinute = min(max(minute, 0), 59)
        slotTimes[slot] = DateComponents(hour: sanitizedHour, minute: sanitizedMinute)
        defaults.set(
            String(format: "%02d:%02d", sanitizedHour, sanitizedMinute),
            forKey: slotTimeKeyPrefix + slot.rawValue
        )
    }

    private static func parseTimeString(_ value: String?) -> DateComponents? {
        guard let value, !value.isEmpty else { return nil }
        let parts = value.split(separator: ":").map { Int($0) }
        guard parts.count == 2, let hour = parts[0], let minute = parts[1] else {
            return nil
        }
        guard (0...23).contains(hour), (0...59).contains(minute) else {
            return nil
        }
        return DateComponents(hour: hour, minute: minute)
    }
}
