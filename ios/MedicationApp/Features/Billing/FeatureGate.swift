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

    // MARK: - Patient Limit Gate (009-free-limit-gates)

    /// Maximum number of patients a free caregiver can register.
    static let freePatientLimit = 1

    /// Returns `true` when the caregiver is allowed to add a new patient.
    ///
    /// - Premium caregivers: always allowed (unlimited).
    /// - Free caregivers: allowed only when `currentPatientCount < freePatientLimit`.
    /// - Unknown state: blocked as a safety measure (must refresh entitlement first).
    ///
    /// - Parameters:
    ///   - entitlementState: The current entitlement state from `EntitlementStore`.
    ///   - currentPatientCount: Number of ACTIVE linked patients.
    /// - Returns: `true` if patient creation should proceed; `false` if paywall should show.
    static func canAddPatient(entitlementState: EntitlementState, currentPatientCount: Int) -> Bool {
        switch entitlementState {
        case .premium:
            return true
        case .free:
            return currentPatientCount < freePatientLimit
        case .unknown:
            return false
        }
    }
}
