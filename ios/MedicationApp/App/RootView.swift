import SwiftUI

struct RootView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        Group {
            switch sessionStore.mode {
            case .none:
                ModeSelectView()
            case .some(.caregiver):
                if sessionStore.caregiverToken == nil {
                    CaregiverAuthChoiceView()
                } else {
                    CaregiverHomeView()
                }
            case .some(.patient):
                if sessionStore.patientToken == nil {
                    LinkCodeEntryView()
                } else {
                    PatientReadOnlyView()
                }
            }
        }
    }
}
