import Foundation

extension Notification.Name {
    static let presetTimesUpdated = Notification.Name("presetTimesUpdated")
    static let medicationUpdated = Notification.Name("medicationUpdated")
    static let authFailure = Notification.Name("authFailure")
    static let caregiverDidLogin = Notification.Name("caregiverDidLogin")
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

    private(set) var currentPatientId: String?

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

    /// Switch the patient context and reload slot times scoped to the patient.
    /// Falls back to global settings when no patient-specific setting exists.
    func switchPatient(_ patientId: String?) {
        currentPatientId = patientId
        reloadSlotTimes()
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
            forKey: slotTimeKey(for: slot)
        )
    }

    // MARK: - Patient-scoped key helpers

    /// Returns the UserDefaults key for a slot time.
    /// When a patient is selected, uses `notif.slotTime.{patientId}.{slot}`;
    /// otherwise uses the global key `notif.slotTime.{slot}`.
    private func slotTimeKey(for slot: NotificationSlot) -> String {
        if let patientId = currentPatientId, !patientId.isEmpty {
            return "\(slotTimeKeyPrefix)\(patientId).\(slot.rawValue)"
        }
        return slotTimeKeyPrefix + slot.rawValue
    }

    /// Reload slot times from UserDefaults using the current patient context.
    /// Falls back to global settings, then to built-in defaults.
    private func reloadSlotTimes() {
        var times: [NotificationSlot: DateComponents] = [:]
        for slot in NotificationSlot.allCases {
            let patientKey = slotTimeKey(for: slot)
            let patientValue = defaults.string(forKey: patientKey)
            if let parsed = Self.parseTimeString(patientValue) {
                times[slot] = parsed
            } else {
                // Fall back to global setting
                let globalValue = defaults.string(forKey: slotTimeKeyPrefix + slot.rawValue)
                if let parsed = Self.parseTimeString(globalValue) {
                    times[slot] = parsed
                } else {
                    times[slot] = DateComponents(
                        hour: slot.hourMinute.hour,
                        minute: slot.hourMinute.minute
                    )
                }
            }
        }
        slotTimes = times
    }

    func slotTimeQueryItems() -> [URLQueryItem] {
        NotificationSlot.allCases.map { slot in
            let time = slotTime(for: slot)
            let value = String(format: "%02d:%02d", time.hour, time.minute)
            return URLQueryItem(name: "\(slot.rawValue)Time", value: value)
        }
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
