import SwiftUI

struct PatientReadOnlyView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var notificationRouter: NotificationDeepLinkRouter
    @EnvironmentObject private var reminderBannerPresenter: ReminderBannerPresenter
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedTab: PatientTab = .today
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @State private var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var tutorialStepIndex: Int?

    var body: some View {
        FullScreenContainer(content: {
            NavigationStack {
                ZStack {
                    PatientScreenBackground()

                    switch selectedTab {
                    case .today:
                        PatientTodayView(
                            sessionStore: sessionStore,
                            deepLinkTarget: $deepLinkTarget
                        )
                    case .history:
                        HistoryMonthView(sessionStore: sessionStore)
                case .settings:
                    PatientSettingsView(
                        sessionStore: sessionStore,
                        schedulingCoordinator: schedulingCoordinator,
                        preferencesStore: preferencesStore,
                        onLogout: { sessionStore.clearPatientToken() }
                    )
                    }

                    if let tutorialStepIndex {
                        PatientTutorialSampleView(tab: patientTutorialSteps[tutorialStepIndex].tab)
                            .zIndex(5)
                            .allowsHitTesting(false)

                        GuidedTutorialOverlay(
                            step: patientTutorialSteps[tutorialStepIndex].step,
                            stepIndex: tutorialStepIndex,
                            stepCount: patientTutorialSteps.count,
                            tint: PatientUI.teal,
                            isSeniorFriendly: true,
                            bottomClearance: 104,
                            onPrevious: { moveTutorial(by: -1) },
                            onNext: { moveTutorial(by: 1) },
                            onFinish: { finishTutorial() }
                        )
                        .zIndex(10)
                    }
                }
                .navigationTitle("")
                .navigationBarTitleDisplayMode(.inline)
            }
            .id(selectedTab)
            .safeAreaInset(edge: .bottom) {
                PatientBottomTabBar(
                    selectedTab: $selectedTab,
                    highlightedTab: currentTutorialTab
                )
            }
        }, overlay: schedulingCoordinator.isRefreshing ? AnyView(SchedulingRefreshOverlay()) : nil)
        .onReceive(notificationRouter.$target) { target in
            guard let target else { return }
            selectedTab = .today
            deepLinkTarget = target
            notificationRouter.clear()
        }
        .onReceive(reminderBannerPresenter.$banner) { banner in
            guard let banner else { return }
            if selectedTab == .today {
                deepLinkTarget = NotificationDeepLinkTarget(
                    dateKey: banner.dateKey,
                    slot: banner.slot
                )
            }
        }
        .task {
            startTutorialIfNeeded()
            await refreshNotificationSchedule(trigger: .appLaunch)
        }
        .onAppear {
            startTutorialIfNeeded()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await refreshNotificationSchedule(trigger: .appForeground) }
            }
        }
        .onChange(of: tutorialStepIndex) { _, index in
            guard let index else { return }
            selectedTab = patientTutorialSteps[index].tab
        }
        .accessibilityIdentifier("PatientReadOnlyView")
    }

    private var patientTutorialSteps: [PatientTutorialStep] {
        [
            PatientTutorialStep(
                tab: .today,
                step: GuidedTutorialStep(
                    id: "patient-today",
                    icon: "calendar",
                    title: NSLocalizedString("tutorial.patient.today.title", comment: "Patient today tutorial title"),
                    message: NSLocalizedString("tutorial.patient.today.message", comment: "Patient today tutorial message")
                )
            ),
            PatientTutorialStep(
                tab: .history,
                step: GuidedTutorialStep(
                    id: "patient-history",
                    icon: "clock.fill",
                    title: NSLocalizedString("tutorial.patient.history.title", comment: "Patient history tutorial title"),
                    message: NSLocalizedString("tutorial.patient.history.message", comment: "Patient history tutorial message")
                )
            ),
            PatientTutorialStep(
                tab: .settings,
                step: GuidedTutorialStep(
                    id: "patient-settings",
                    icon: "bell.badge.fill",
                    title: NSLocalizedString("tutorial.patient.settings.title", comment: "Patient settings tutorial title"),
                    message: NSLocalizedString("tutorial.patient.settings.message", comment: "Patient settings tutorial message")
                )
            )
        ]
    }

    private var currentTutorialTab: PatientTab? {
        guard let tutorialStepIndex else { return nil }
        return patientTutorialSteps[tutorialStepIndex].tab
    }

    private func refreshNotificationSchedule(trigger: SchedulingRefreshCoordinator.RefreshTrigger) async {
        guard preferencesStore.masterEnabled else { return }
        let apiClient = APIClient(
            baseURL: SessionStore.resolveBaseURL(),
            sessionStore: sessionStore
        )
        await schedulingCoordinator.refresh(
            apiClient: apiClient,
            includeSecondary: preferencesStore.rereminderEnabled,
            enabledSlots: preferencesStore.enabledSlots(),
            slotTimes: [:],
            caregiverPatientId: nil,
            preferencesStore: preferencesStore,
            trigger: trigger
        )
    }

    private func startTutorialIfNeeded() {
        guard sessionStore.shouldShowModeTutorial(for: .patient)
            || sessionStore.shouldForceModeTutorial(for: .patient) else { return }
        tutorialStepIndex = 0
        selectedTab = patientTutorialSteps[0].tab
    }

    private func moveTutorial(by offset: Int) {
        guard let tutorialStepIndex else { return }
        let nextIndex = tutorialStepIndex + offset
        guard patientTutorialSteps.indices.contains(nextIndex) else {
            finishTutorial()
            return
        }
        withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) {
            self.tutorialStepIndex = nextIndex
        }
    }

    private func finishTutorial() {
        sessionStore.markModeTutorialSeen(for: .patient)
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            tutorialStepIndex = nil
        }
    }

}

private struct PatientTutorialStep {
    let tab: PatientTab
    let step: GuidedTutorialStep
}

private struct PatientTutorialSampleView: View {
    let tab: PatientTab

    var body: some View {
        ZStack {
            PatientScreenBackground()

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    PatientHeader(title: title, subtitle: subtitle, systemImage: icon)
                    content
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 260)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PatientUI.backgroundBottom)
        .ignoresSafeArea(edges: .bottom)
        .transition(.opacity)
    }

    @ViewBuilder
    private var content: some View {
        switch tab {
        case .today:
            PatientCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text(NSLocalizedString("patient.today.next.title", comment: "Next medication title"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.primary)

                    HStack(alignment: .center, spacing: 16) {
                        Image(systemName: "clock")
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(PatientUI.blue)
                            .frame(width: 66, height: 66)
                            .background(PatientUI.blue.opacity(0.10), in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text("昼のお薬")
                                .font(.headline.weight(.bold))
                            Text("12:30")
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundStyle(PatientUI.tealDark)
                                .lineLimit(1)
                        }
                        Spacer()
                    }
                    PatientStatusPill(text: "未記録", color: PatientUI.orange, systemImage: "circle")

                    Text("この時間のお薬")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.secondary)
                    sampleMedicineRow(name: "血圧の薬 5 mg", detail: "1回1錠", color: PatientUI.teal)
                    sampleMedicineRow(name: "胃薬", detail: "1回1錠", color: PatientUI.blue)

                    Text(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record button"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 52)
                        .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
        case .history:
            PatientCard {
                VStack(alignment: .leading, spacing: 14) {
                    sampleSectionTitle(NSLocalizedString("patient.history.today.progress.title", comment: "Today progress"), systemImage: "chart.bar.fill")
                    HStack(spacing: 12) {
                        sampleMetric(value: "2/3", label: "回分 記録済み", color: PatientUI.teal)
                        sampleMetric(value: "1", label: "未記録", color: PatientUI.orange)
                    }
                    Text(NSLocalizedString("patient.history.recent.title", comment: "Recent records title"))
                        .font(.headline.weight(.bold))
                    sampleMedicineRow(name: "朝のお薬", detail: "08:00 ・ 記録済み", color: PatientUI.teal)
                    sampleMedicineRow(name: "昼のお薬", detail: "12:30 ・ 未記録", color: PatientUI.orange)
                    sampleMedicineRow(name: "夜のお薬", detail: "20:00 ・ 予定", color: PatientUI.blue)
                }
            }
        case .settings:
            PatientCard {
                VStack(alignment: .leading, spacing: 16) {
                    sampleSectionTitle("お薬の通知", systemImage: "bell.badge.fill")
                    sampleSettingRow(title: "通知を有効にする", detail: "飲む時間にこの端末へ通知します", systemImage: "bell.fill")
                    sampleSettingRow(title: "再通知（15分後）", detail: "飲み忘れ防止のためもう一度知らせます", systemImage: "bell.and.waves.left.and.right.fill")
                    sampleSettingRow(title: "連携中", detail: "家族と服薬記録を共有しています", systemImage: "person.2.fill")
                }
            }
        }
    }

    private var title: String {
        switch tab {
        case .today:
            return "今日のお薬"
        case .history:
            return "履歴"
        case .settings:
            return "設定"
        }
    }

    private var subtitle: String {
        switch tab {
        case .today:
            return "飲む予定と記録ボタンが表示されます"
        case .history:
            return "飲んだ記録を確認できます"
        case .settings:
            return "通知と連携状態を確認できます"
        }
    }

    private var icon: String {
        switch tab {
        case .today:
            return "calendar"
        case .history:
            return "clock.fill"
        case .settings:
            return "gearshape.fill"
        }
    }

    private func sampleSectionTitle(_ text: String, systemImage: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .foregroundStyle(PatientUI.teal)
            Text(text)
                .font(.headline.weight(.bold))
        }
    }

    private func sampleMedicineRow(name: String, detail: String, color: Color) -> some View {
        HStack(spacing: 12) {
            Circle()
                .fill(color.opacity(0.16))
                .frame(width: 42, height: 42)
                .overlay {
                    Image(systemName: "pills.fill")
                        .foregroundStyle(color)
                }
            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .font(.headline.weight(.bold))
                Text(detail)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func sampleMetric(value: String, label: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.title.weight(.bold))
                .foregroundStyle(color)
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func sampleSettingRow(title: String, detail: String, systemImage: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.title3.weight(.bold))
                .foregroundStyle(PatientUI.teal)
                .frame(width: 44, height: 44)
                .background(PatientUI.teal.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline.weight(.bold))
                Text(detail)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }
}

struct PatientSettingsView: View {
    @EnvironmentObject private var globalBannerPresenter: GlobalBannerPresenter
    private let sessionStore: SessionStore
    private let apiClient: APIClient
    @ObservedObject private var schedulingCoordinator: SchedulingRefreshCoordinator
    @StateObject private var permissionManager = NotificationPermissionManager()
    @ObservedObject private var preferencesStore: NotificationPreferencesStore
    @State private var showingLogoutConfirm = false
    let onLogout: () -> Void

    init(
        sessionStore: SessionStore,
        schedulingCoordinator: SchedulingRefreshCoordinator,
        preferencesStore: NotificationPreferencesStore,
        onLogout: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.apiClient = APIClient(
            baseURL: SessionStore.resolveBaseURL(),
            sessionStore: sessionStore
        )
        self.schedulingCoordinator = schedulingCoordinator
        self.preferencesStore = preferencesStore
        self.onLogout = onLogout
    }

    var body: some View {
        let notificationsDisabled = permissionManager.status == .denied
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PatientHeader(
                    title: NSLocalizedString("patient.readonly.settings.title", comment: "Settings title"),
                    subtitle: NSLocalizedString("patient.settings.subtitle", comment: "Settings subtitle"),
                    systemImage: "gearshape.fill"
                )

                PatientCard {
                    VStack(alignment: .leading, spacing: 18) {
                        settingsSectionTitle(
                            NSLocalizedString("patient.settings.notifications.card.title", comment: "Notification card title"),
                            systemImage: "bell.badge.fill",
                            color: PatientUI.teal
                        )

                        largeToggle(
                            title: NSLocalizedString("patient.settings.notifications.master", comment: "Enable notifications"),
                            subtitle: NSLocalizedString("patient.settings.notifications.master.note", comment: "Master note"),
                            systemImage: "bell.fill",
                            isOn: $preferencesStore.masterEnabled
                        )
                        .onChange(of: preferencesStore.masterEnabled) { _, enabled in
                            Task { await handleMasterToggle(enabled) }
                        }
                    }
                }
                .disabled(notificationsDisabled)

                PatientCard {
                    settingsInfoRow(
                        title: NSLocalizedString("patient.settings.linked.title", comment: "Linked title"),
                        subtitle: NSLocalizedString("patient.settings.linked.note", comment: "Linked note"),
                        systemImage: "person.2.fill",
                        color: PatientUI.teal
                    )
                }

                if notificationsDisabled {
                    PatientCard(accent: PatientUI.red) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(PatientUI.red)
                            Text(NSLocalizedString("patient.settings.notifications.permission.denied", comment: "Permission denied guidance"))
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Button {
                    showingLogoutConfirm = true
                } label: {
                    Label(NSLocalizedString("common.logout", comment: "Logout"), systemImage: "rectangle.portrait.and.arrow.right")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 58)
                        .background(PatientUI.red, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 130)
        }
        .refreshable {
            await permissionManager.refreshStatus()
        }
        .alert(
            NSLocalizedString("patient.logout.confirm.title", comment: "Logout confirm title"),
            isPresented: $showingLogoutConfirm
        ) {
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
            Button(NSLocalizedString("patient.logout.confirm.action", comment: "Logout confirm action"), role: .destructive) {
                onLogout()
                globalBannerPresenter.show(
                    message: NSLocalizedString("patient.logout.toast", comment: "Logout toast"),
                    duration: 2
                )
            }
        } message: {
            Text(NSLocalizedString("patient.logout.confirm.message", comment: "Logout confirm message"))
        }
        .onAppear {
            Task { await permissionManager.refreshStatus() }
        }
        .accessibilityIdentifier("PatientSettingsView")
    }

    private func largeToggle(
        title: String,
        subtitle: String,
        systemImage: String,
        isOn: Binding<Bool>
    ) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(PatientUI.teal)
                    .frame(width: 44, height: 44)
                    .background(PatientUI.teal.opacity(0.12), in: Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .toggleStyle(.switch)
    }

    private func settingsSectionTitle(_ title: String, systemImage: String, color: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.headline.weight(.bold))
                .foregroundStyle(color)
            Text(title)
                .font(.title3.weight(.bold))
                .foregroundStyle(.primary)
        }
    }

    private func settingsInfoRow(title: String, subtitle: String, systemImage: String, color: Color) -> some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.title2.weight(.bold))
                .foregroundStyle(color)
                .frame(width: 48, height: 48)
                .background(color.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Image(systemName: "checkmark.circle.fill")
                .font(.title2.weight(.bold))
                .foregroundStyle(PatientUI.teal)
                .accessibilityHidden(true)
        }
    }

    private func settingsNavigationRow(title: String, subtitle: String, systemImage: String, color: Color) -> some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.title2.weight(.bold))
                .foregroundStyle(color)
                .frame(width: 48, height: 48)
                .background(color.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.headline.weight(.bold))
                .foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
    }

    private func handleMasterToggle(_ enabled: Bool) async {
        if enabled {
            let granted = await permissionManager.requestAuthorizationIfNeeded()
            if !granted {
                preferencesStore.masterEnabled = false
                return
            }
            await rescheduleIfNeeded()
        } else {
            let scheduler = NotificationScheduler()
            await scheduler.schedule(planEntries: [], now: Date())
        }
    }

    private func rescheduleIfNeeded() async {
        guard preferencesStore.masterEnabled else { return }
        await schedulingCoordinator.refresh(
            apiClient: apiClient,
            includeSecondary: preferencesStore.rereminderEnabled,
            enabledSlots: preferencesStore.enabledSlots(),
            slotTimes: [:],
            caregiverPatientId: nil,
            preferencesStore: preferencesStore,
            trigger: .settingsChange
        )
    }
}

enum PatientTab: Hashable {
    case today
    case history
    case settings
}

enum PatientUI {
    static let teal = Color(red: 0.0, green: 0.55, blue: 0.50)
    static let tealDark = Color(red: 0.0, green: 0.43, blue: 0.40)
    static let blue = Color(red: 0.10, green: 0.45, blue: 0.82)
    static let orange = Color(red: 0.94, green: 0.42, blue: 0.0)
    static let indigo = Color(red: 0.34, green: 0.32, blue: 0.78)
    static let red = Color(red: 0.86, green: 0.18, blue: 0.20)
    static let backgroundTop = Color(red: 0.93, green: 0.98, blue: 1.0)
    static let backgroundBottom = Color(.systemGroupedBackground)
    static let cardStroke = Color.black.opacity(0.10)
    static let cardShadow = Color.black.opacity(0.07)
}

struct PatientScreenBackground: View {
    var body: some View {
        LinearGradient(
            colors: [PatientUI.backgroundTop, PatientUI.backgroundBottom],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

struct PatientHeader: View {
    let title: String
    let subtitle: String
    let systemImage: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 62, height: 62)
                .background(PatientUI.teal, in: Circle())
                .overlay {
                    Circle().stroke(Color.white, lineWidth: 5)
                }
                .shadow(color: PatientUI.cardShadow, radius: 8, y: 3)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(subtitle)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
            }
            Spacer(minLength: 0)
        }
    }
}

struct PatientCard<Content: View>: View {
    var accent: Color?
    let content: Content

    init(accent: Color? = nil, @ViewBuilder content: () -> Content) {
        self.accent = accent
        self.content = content()
    }

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke((accent ?? PatientUI.cardStroke).opacity(accent == nil ? 1 : 0.55), lineWidth: accent == nil ? 1 : 1.5)
            }
            .shadow(color: PatientUI.cardShadow, radius: 12, y: 5)
    }
}

struct PatientStatusPill: View {
    let text: String
    let color: Color
    var systemImage: String?

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.caption.weight(.bold))
            }
            Text(text)
                .font(.caption.weight(.bold))
                .lineLimit(1)
                .minimumScaleFactor(0.74)
        }
        .foregroundStyle(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(color.opacity(0.13), in: Capsule())
    }
}

private struct PatientBottomTabBar: View {
    @Binding var selectedTab: PatientTab
    var highlightedTab: PatientTab?

    var body: some View {
        HStack(spacing: 12) {
            tabButton(
                title: NSLocalizedString("patient.readonly.today.tab", comment: "Today tab"),
                systemImage: "calendar",
                isSelected: selectedTab == .today,
                isHighlighted: highlightedTab == .today
            ) {
                selectedTab = .today
            }
            tabButton(
                title: NSLocalizedString("patient.readonly.history.tab", comment: "History tab"),
                systemImage: "clock",
                isSelected: selectedTab == .history,
                isHighlighted: highlightedTab == .history
            ) {
                selectedTab = .history
            }
            tabButton(
                title: NSLocalizedString("patient.readonly.settings.tab", comment: "Settings tab"),
                systemImage: "gearshape",
                isSelected: selectedTab == .settings,
                isHighlighted: highlightedTab == .settings
            ) {
                selectedTab = .settings
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(PatientUI.cardStroke, lineWidth: 1)
        }
        .shadow(color: PatientUI.cardShadow, radius: 14, y: 5)
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
    }

    private func tabButton(
        title: String,
        systemImage: String,
        isSelected: Bool,
        isHighlighted: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: 28, weight: .bold))
                Text(title)
                    .font(.headline.weight(.bold))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.82)
            }
            .foregroundStyle(isSelected ? PatientUI.teal : Color.secondary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 74)
            .padding(.horizontal, 10)
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .background(isSelected ? AnyShapeStyle(PatientUI.teal.opacity(0.13)) : AnyShapeStyle(Color.clear), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                if isHighlighted {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(PatientUI.teal, lineWidth: 3)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

struct PatientHistoryPlaceholderView: View {
    var body: some View {
        VStack {
            Spacer(minLength: 0)
            VStack(spacing: 12) {
                Text(NSLocalizedString("patient.readonly.history.title", comment: "History title"))
                    .font(.title3.weight(.semibold))
                Text(NSLocalizedString("patient.readonly.history.message", comment: "History message"))
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("PatientHistoryPlaceholderView")
    }
}
