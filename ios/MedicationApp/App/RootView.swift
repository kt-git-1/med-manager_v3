import SwiftUI

struct RootView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        Group {
            switch sessionStore.mode {
            case .none:
                ModeSelectView()
            case .some(.caregiver):
                CaregiverLoginView()
            case .some(.patient):
                LinkCodeEntryView()
            }
        }
    }
}
