import Foundation
import SwiftUI

// MARK: - Application-wide Constants

enum AppConstants {

    // MARK: - TimeZone / Locale

    /// Default timezone used throughout the app (schedule, history, notifications, etc.).
    static let defaultTimeZone: TimeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current

    /// Japanese locale for user-facing date formatting.
    static let japaneseLocale = Locale(identifier: "ja_JP")

    /// POSIX locale for machine-readable date formatting (ISO 8601, API keys, etc.).
    static let posixLocale = Locale(identifier: "en_US_POSIX")

    // MARK: - API / Auth

    /// Prefix prepended to a Supabase JWT to identify caregiver tokens.
    static let caregiverTokenPrefix = "caregiver-"

    /// Fallback base URL used when API_BASE_URL is not configured.
    static let defaultAPIBaseURL = URL(string: "http://localhost:3000")!

    /// Patient token refresh interval in seconds.
    static let patientTokenRefreshInterval: TimeInterval = 600 // 10 minutes

    // MARK: - Notification

    /// Identifier prefix for scheduled local notifications.
    static let notificationIdentifierPrefix = "notif:"

    /// How many days ahead to schedule notifications.
    static let notificationLookaheadDays = 7

    /// Delay for secondary (follow-up) reminder notifications in seconds.
    static let secondaryReminderDelay: TimeInterval = 15 * 60 // 15 minutes

    /// WebSocket heartbeat interval in seconds.
    static let websocketHeartbeatInterval: TimeInterval = 25

    // MARK: - UI Durations

    /// Default toast message display duration in seconds.
    static let toastDuration: TimeInterval = 2

    /// Duration to highlight a slot in the Today view.
    static let slotHighlightDuration: TimeInterval = 3

    /// Default banner display duration in seconds.
    static let bannerDefaultDuration: TimeInterval = 3

    // MARK: - UI Styling

    /// Standard overlay opacity for loading states.
    static let overlayOpacity: Double = 0.2

    // MARK: - Slot Colors

    /// Returns the canonical color for a given notification slot.
    static func slotColor(for slot: NotificationSlot?) -> Color {
        switch slot {
        case .morning:
            return .orange
        case .noon:
            return .blue
        case .evening:
            return .purple
        case .bedtime:
            return .indigo
        case .none:
            return .gray
        }
    }
}
