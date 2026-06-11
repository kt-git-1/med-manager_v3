import SwiftUI

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

}

struct GuidedTutorialStep: Identifiable {
    let id: String
    let icon: String
    let title: String
    let message: String
}

struct GuidedTutorialOverlay: View {
    let step: GuidedTutorialStep
    let stepIndex: Int
    let stepCount: Int
    let tint: Color
    var isSeniorFriendly = false
    var bottomClearance: CGFloat = 4
    var onSkip: (() -> Void)?
    let onPrevious: () -> Void
    let onNext: () -> Void
    let onFinish: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.18)
                .ignoresSafeArea()
                .onTapGesture {}

            VStack(alignment: .leading, spacing: cardSpacing) {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: step.icon)
                        .font(iconFont)
                        .foregroundStyle(tint)
                        .frame(width: iconSize, height: iconSize)
                        .background(tint.opacity(0.12), in: Circle())

                    VStack(alignment: .leading, spacing: isSeniorFriendly ? 7 : 5) {
                        Text(step.title)
                            .font(titleFont)
                            .foregroundStyle(.primary)
                            .fixedSize(horizontal: false, vertical: true)

                        Text(step.message)
                            .font(messageFont)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }

                HStack(spacing: 6) {
                    ForEach(0..<stepCount, id: \.self) { index in
                        Capsule()
                            .fill(index == stepIndex ? tint : Color.secondary.opacity(0.25))
                            .frame(width: index == stepIndex ? 22 : 7, height: 7)
                    }
                    Spacer(minLength: 0)
                    Text(String(format: "%d/%d", stepIndex + 1, stepCount))
                        .font(isSeniorFriendly ? .subheadline.weight(.bold) : .caption.weight(.bold))
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 12) {
                    Button(action: onSkip ?? onFinish) {
                        Text(NSLocalizedString("tutorial.skip", comment: "Skip tutorial action"))
                            .font(buttonFont)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: buttonHeight)
                    }
                    .buttonStyle(.plain)

                    if stepIndex > 0 {
                        Button(action: onPrevious) {
                            Image(systemName: "chevron.left")
                                .font(buttonFont)
                                .foregroundStyle(tint)
                                .frame(width: buttonHeight, height: buttonHeight)
                                .background(tint.opacity(0.10), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(NSLocalizedString("tutorial.previous", comment: "Previous tutorial action"))
                    }

                    Button(action: onNext) {
                        Label(
                            stepIndex == stepCount - 1
                                ? NSLocalizedString("tutorial.done", comment: "Done tutorial action")
                                : NSLocalizedString("tutorial.next", comment: "Next tutorial action"),
                            systemImage: stepIndex == stepCount - 1 ? "checkmark" : "chevron.right"
                        )
                        .font(buttonFont)
                        .labelStyle(.titleAndIcon)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: buttonHeight)
                        .background(tint, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(cardPadding)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(tint.opacity(0.18), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.14), radius: 16, y: 6)
            .padding(.horizontal, 12)
            .padding(.bottom, bottomClearance)
        }
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    private var iconSize: CGFloat {
        isSeniorFriendly ? 48 : 40
    }

    private var iconFont: Font {
        isSeniorFriendly ? .title2.weight(.bold) : .headline.weight(.bold)
    }

    private var titleFont: Font {
        isSeniorFriendly ? .title3.weight(.bold) : .headline.weight(.bold)
    }

    private var messageFont: Font {
        isSeniorFriendly ? .body.weight(.semibold) : .footnote.weight(.semibold)
    }

    private var buttonFont: Font {
        isSeniorFriendly ? .headline.weight(.bold) : .subheadline.weight(.bold)
    }

    private var buttonHeight: CGFloat {
        isSeniorFriendly ? 50 : 42
    }

    private var cardSpacing: CGFloat {
        isSeniorFriendly ? 14 : 12
    }

    private var cardPadding: CGFloat {
        isSeniorFriendly ? 16 : 14
    }
}
