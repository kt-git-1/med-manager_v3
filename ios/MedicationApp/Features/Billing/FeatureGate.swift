import Foundation

// MARK: - Entitlement Tier

enum EntitlementTier: Comparable, Sendable {
    case free
    case premium
    case pro
}

// MARK: - Feature Gate

enum FeatureGate: CaseIterable, Sendable {
    case multiplePatients
    case extendedHistory
    case pdfExport
    case enhancedAlerts
    case escalationPush

    var requiredTier: EntitlementTier {
        switch self {
        case .multiplePatients: return .premium
        case .extendedHistory:  return .premium
        case .pdfExport:        return .premium
        case .enhancedAlerts:   return .premium
        case .escalationPush:   return .pro
        }
    }

    /// Returns `true` when the user's current entitlement state meets or exceeds
    /// the gate's required tier.
    ///
    /// - Note: `.pro` tier is always locked in this feature (008). `.unknown`
    ///   state locks all gates as a safety measure.
    static func isUnlocked(_ gate: FeatureGate, for state: EntitlementState) -> Bool {
        switch state {
        case .unknown, .free:
            return false
        case .premium:
            return gate.requiredTier <= .premium
        }
    }
}
