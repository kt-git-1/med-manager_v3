import Foundation
import SwiftUI
import UIKit

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

    /// Billing is intentionally disabled for the initial public release.
    static let billingEnabled = false

    /// Public legal and support pages used from App Store metadata and in-app settings.
    static let privacyPolicyURL = URL(string: "https://www.okusuri-mimamori.com/privacy")!
    static let termsURL = URL(string: "https://www.okusuri-mimamori.com/terms")!
    static let supportURL = URL(string: "https://www.okusuri-mimamori.com/support")!

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

    // MARK: - Number Formatting

    /// Format a decimal number for display, removing unnecessary trailing zeros.
    /// Examples: 1.0 → "1", 0.5 → "0.5", 2.5 → "2.5"
    static func formatDecimal(_ value: Double) -> String {
        if value.truncatingRemainder(dividingBy: 1) == 0 {
            return String(Int(value))
        }
        return String(format: "%.1f", value)
    }

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

// MARK: - App Theme

enum AppTheme {
    static let primaryTeal = Color.dynamic(
        light: UIColor(red: 0.0, green: 0.55, blue: 0.50, alpha: 1),
        dark: UIColor(red: 0.0, green: 0.62, blue: 0.60, alpha: 1)
    )

    static let primaryTealText = Color.dynamic(
        light: UIColor(red: 0.0, green: 0.43, blue: 0.40, alpha: 1),
        dark: UIColor(red: 0.54, green: 0.90, blue: 0.87, alpha: 1)
    )

    static let blue = Color(red: 0.10, green: 0.45, blue: 0.82)
    static let caregiverBlue = Color(red: 0.12, green: 0.48, blue: 0.82)
    static let orange = Color(red: 0.94, green: 0.42, blue: 0.0)
    static let indigo = Color(red: 0.34, green: 0.32, blue: 0.78)
    static let patientRed = Color(red: 0.86, green: 0.18, blue: 0.20)
    static let caregiverRed = Color(red: 0.82, green: 0.16, blue: 0.16)

    static let screenBackground = Color.dynamic(
        light: UIColor(red: 0.95, green: 0.98, blue: 0.99, alpha: 1),
        dark: UIColor(red: 0.14, green: 0.18, blue: 0.19, alpha: 1)
    )

    static let cardBackground = Color.dynamic(
        light: .secondarySystemGroupedBackground,
        dark: UIColor(red: 0.22, green: 0.27, blue: 0.28, alpha: 1)
    )

    static let elevatedBackground = Color.dynamic(
        light: .tertiarySystemGroupedBackground,
        dark: UIColor(red: 0.27, green: 0.31, blue: 0.32, alpha: 1)
    )

    static let readableSecondaryText = Color.dynamic(
        light: .secondaryLabel,
        dark: UIColor(red: 0.82, green: 0.85, blue: 0.87, alpha: 1)
    )

    static let cardStroke = Color.primary.opacity(0.10)
    static let patientCardShadow = Color.black.opacity(0.07)
    static let caregiverCardShadow = Color.black.opacity(0.06)
}

extension Color {
    static func dynamic(light: UIColor, dark: UIColor) -> Color {
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? dark : light
        })
    }

    static let readableSecondaryText = AppTheme.readableSecondaryText
}
