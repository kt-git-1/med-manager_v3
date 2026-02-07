import SwiftUI
import UIKit

struct RootView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var globalBannerPresenter: GlobalBannerPresenter
    @EnvironmentObject private var caregiverSessionController: CaregiverSessionController
    @Environment(\.scenePhase) private var scenePhase

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
        .contentShape(Rectangle())
        .simultaneousGesture(
            TapGesture().onEnded {
                dismissKeyboard()
            }
        )
        .overlay(alignment: .top) {
            if let banner = globalBannerPresenter.banner {
                GlobalBannerView(banner: banner)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: globalBannerPresenter.banner)
        .onAppear {
            caregiverSessionController.updateScenePhase(scenePhase)
        }
        .onChange(of: scenePhase) { _, phase in
            caregiverSessionController.updateScenePhase(phase)
        }
        .onReceive(NotificationCenter.default.publisher(for: .authFailure)) { _ in
            globalBannerPresenter.show(
                message: NSLocalizedString("common.error.unexpected", comment: "Unexpected error"),
                duration: 6
            )
        }
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}
