import SwiftUI
import UIKit

struct RootView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var globalBannerPresenter: GlobalBannerPresenter
    @EnvironmentObject private var toastPresenter: ToastPresenter
    @EnvironmentObject private var caregiverSessionController: CaregiverSessionController
    @Environment(\.scenePhase) private var scenePhase
    var entitlementStore: EntitlementStore?

    var body: some View {
        Group {
            switch sessionStore.mode {
            case .none:
                ModeSelectView()
            case .some(.caregiver):
                if sessionStore.caregiverToken == nil {
                    CaregiverAuthChoiceView()
                } else {
                    CaregiverHomeView(entitlementStore: entitlementStore)
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
            if let toast = toastPresenter.toast {
                ToastOverlayView(toast: toast)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: toastPresenter.toast)
        .sensoryFeedback(.success, trigger: toastPresenter.successFeedbackTrigger)
        .task {
            if sessionStore.mode == .caregiver {
                await sessionStore.refreshCaregiverTokenIfNeeded()
                if sessionStore.caregiverToken != nil {
                    await entitlementStore?.refresh()
                }
            }
        }
        .onAppear {
            caregiverSessionController.updateScenePhase(scenePhase)
        }
        .onChange(of: scenePhase) { _, phase in
            caregiverSessionController.updateScenePhase(phase)
            if phase == .active && sessionStore.mode == .caregiver {
                Task {
                    await sessionStore.refreshCaregiverTokenIfNeeded()
                    if sessionStore.caregiverToken != nil {
                        await entitlementStore?.refresh()
                    }
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .authFailure)) { _ in
            globalBannerPresenter.show(
                message: NSLocalizedString("common.error.unexpected", comment: "Unexpected error"),
                duration: 6
            )
        }
        .onReceive(NotificationCenter.default.publisher(for: .caregiverDidLogin)) { _ in
            Task { await entitlementStore?.refresh() }
        }
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}
