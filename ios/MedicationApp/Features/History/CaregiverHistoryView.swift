import SwiftUI

struct CaregiverHistoryView: View {
    private let sessionStore: SessionStore
    private let entitlementStore: EntitlementStore?
    private let patientName: String?
    private let hasAnyPatient: Bool?
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    private let onOpenPatients: () -> Void
    private let onCreatePatient: () -> Void

    init(
        sessionStore: SessionStore,
        entitlementStore: EntitlementStore? = nil,
        patientName: String? = nil,
        hasAnyPatient: Bool? = nil,
        deepLinkTarget: Binding<NotificationDeepLinkTarget?> = .constant(nil),
        onOpenPatients: @escaping () -> Void,
        onCreatePatient: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.entitlementStore = entitlementStore
        self.patientName = patientName
        self.hasAnyPatient = hasAnyPatient
        self._deepLinkTarget = deepLinkTarget
        self.onOpenPatients = onOpenPatients
        self.onCreatePatient = onCreatePatient
    }

    var body: some View {
        CaregiverScreenBackground {
            if sessionStore.currentPatientId == nil, hasAnyPatient == false {
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
