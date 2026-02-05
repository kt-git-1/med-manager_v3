import Foundation

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

    private let defaults: UserDefaults
    private let masterKey = "notif.masterEnabled"
    private let rereminderKey = "notif.rereminderEnabled"
    private let slotKeyPrefix = "notif.slot."

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
}
