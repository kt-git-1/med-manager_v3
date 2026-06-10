import SwiftUI

struct CaregiverHistoryView: View {
    private let sessionStore: SessionStore
    private let entitlementStore: EntitlementStore?
    private let patientName: String?
    private let hasAnyPatient: Bool?
    private let patientListErrorMessage: String?
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    private let onRetryPatients: () -> Void
    private let onOpenPatients: () -> Void
    private let onCreatePatient: () -> Void

    init(
        sessionStore: SessionStore,
        entitlementStore: EntitlementStore? = nil,
        patientName: String? = nil,
        hasAnyPatient: Bool? = nil,
        patientListErrorMessage: String? = nil,
        deepLinkTarget: Binding<NotificationDeepLinkTarget?> = .constant(nil),
        onRetryPatients: @escaping () -> Void = {},
        onOpenPatients: @escaping () -> Void,
        onCreatePatient: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.entitlementStore = entitlementStore
        self.patientName = patientName
        self.hasAnyPatient = hasAnyPatient
        self.patientListErrorMessage = patientListErrorMessage
        self._deepLinkTarget = deepLinkTarget
        self.onRetryPatients = onRetryPatients
        self.onOpenPatients = onOpenPatients
        self.onCreatePatient = onCreatePatient
    }

    var body: some View {
        CaregiverScreenBackground {
            if let patientListErrorMessage, sessionStore.currentPatientId == nil {
                CaregiverDataUnavailableView(
                    message: patientListErrorMessage,
                    onRetry: { onRetryPatients() },
                    onReturnToLogin: { sessionStore.returnToCaregiverLogin() }
                )
            } else if sessionStore.currentPatientId == nil, hasAnyPatient == false {
                CaregiverNoPatientEmptyStateView(onCreatePatient: onCreatePatient)
            } else if sessionStore.currentPatientId == nil {
                CaregiverPatientSelectionRequiredView(
                    systemImage: "clock.badge.questionmark",
                    onOpenPatients: onOpenPatients
                )
                .accessibilityIdentifier("CaregiverHistoryEmptyState")
            } else {
                HistoryMonthView(
                    sessionStore: sessionStore,
                    entitlementStore: entitlementStore,
                    patientName: patientName,
                    deepLinkTarget: $deepLinkTarget
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .accessibilityIdentifier("CaregiverHistoryView")
    }
}
