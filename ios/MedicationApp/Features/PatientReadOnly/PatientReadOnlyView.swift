import SafariServices
import SwiftUI

struct PatientReadOnlyView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var notificationRouter: NotificationDeepLinkRouter
    @EnvironmentObject private var reminderBannerPresenter: ReminderBannerPresenter
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedTab: PatientTab = .today
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @StateObject private var permissionManager = NotificationPermissionManager()
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
                        onLogout: { logoutPatient() }
                    )
                    }

                    if let tutorialStepIndex {
                        PatientTutorialSampleView(tab: patientTutorialSteps[tutorialStepIndex].tab)
                            .zIndex(5)
                            .allowsHitTesting(false)

                        if !isMarketingScreenshotPreview {
                            GuidedTutorialOverlay(
                                step: patientTutorialSteps[tutorialStepIndex].step,
                                stepIndex: tutorialStepIndex,
                                stepCount: patientTutorialSteps.count,
                                tint: PatientUI.teal,
                                isSeniorFriendly: true,
                                bottomClearance: 104,
                                skipTitle: isCurrentTutorialNotificationStep
                                    ? NSLocalizedString("tutorial.notification.later", comment: "Set notification later")
                                    : nil,
                                finalPrimaryTitle: isCurrentTutorialNotificationStep
                                    ? NSLocalizedString("tutorial.notification.enable", comment: "Enable notification tutorial action")
                                    : nil,
                                finalPrimarySystemImage: isCurrentTutorialNotificationStep ? "bell.badge.fill" : nil,
                                onSkip: {
                                    finishTutorial(skipped: true)
                                },
                                onPrevious: { moveTutorial(by: -1) },
                                onNext: { handleTutorialNext() },
                                onFinish: {
                                    finishTutorial(skipped: false)
                                }
                            )
                            .zIndex(10)
                        }
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
            if !isMarketingScreenshotPreview {
                await refreshNotificationSchedule(trigger: .appLaunch)
            }
        }
        .onAppear {
            AnalyticsService.shared.logPatientTabViewed(analyticsTab(for: selectedTab))
            startTutorialIfNeeded()
        }
        .onChange(of: selectedTab) { _, tab in
            AnalyticsService.shared.logPatientTabViewed(analyticsTab(for: tab))
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active && !isMarketingScreenshotPreview {
                Task { await refreshNotificationSchedule(trigger: .appForeground) }
            }
        }
        .onChange(of: tutorialStepIndex) { _, index in
            guard let index else { return }
            AnalyticsService.shared.logTutorialStepViewed(mode: .patient, step: index + 1)
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
            ),
            PatientTutorialStep(
                tab: .today,
                step: GuidedTutorialStep(
                    id: "patient-notification-permission",
                    icon: "bell.badge.fill",
                    title: NSLocalizedString("tutorial.patient.notification.title", comment: "Patient notification permission tutorial title"),
                    message: NSLocalizedString("tutorial.patient.notification.message", comment: "Patient notification permission tutorial message")
                )
            )
        ]
    }

    private func logoutPatient() {
        deepLinkTarget = nil
        notificationRouter.clear()
        reminderBannerPresenter.dismiss()
        Task {
            await schedulingCoordinator.cancelScheduledNotifications()
            let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
            try? await apiClient.revokePatientSession()
            sessionStore.clearPatientToken()
        }
    }

    private var currentTutorialTab: PatientTab? {
        guard let tutorialStepIndex else { return nil }
        return patientTutorialSteps[tutorialStepIndex].tab
    }

    private var isCurrentTutorialNotificationStep: Bool {
        guard let tutorialStepIndex else { return false }
        return patientTutorialSteps[tutorialStepIndex].step.id == "patient-notification-permission"
    }

    private var isMarketingScreenshotPreview: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains { $0.hasPrefix("-PatientMarketingScreenshot.") }
        #else
        false
        #endif
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
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-PatientMarketingScreenshot.today") {
            tutorialStepIndex = 0
            selectedTab = .today
            return
        }
        #endif
        guard sessionStore.shouldShowModeTutorial(for: .patient)
            || sessionStore.shouldForceModeTutorial(for: .patient) else { return }
        tutorialStepIndex = 0
        AnalyticsService.shared.logTutorialStarted(mode: .patient)
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

    private func handleTutorialNext() {
        if isCurrentTutorialNotificationStep {
            Task { await enableNotificationsFromTutorial() }
        } else {
            moveTutorial(by: 1)
        }
    }

    private func enableNotificationsFromTutorial() async {
        let granted = await permissionManager.requestAuthorizationIfNeeded()
        preferencesStore.masterEnabled = granted
        if granted {
            await refreshNotificationSchedule(trigger: .settingsChange)
        }
        finishTutorial()
    }

    private func finishTutorial(skipped: Bool = false) {
        AnalyticsService.shared.logTutorialFinished(mode: .patient, skipped: skipped)
        sessionStore.markModeTutorialSeen(for: .patient)
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            tutorialStepIndex = nil
        }
    }

    private func analyticsTab(for tab: PatientTab) -> AnalyticsPatientTab {
        switch tab {
        case .today: return .today
        case .history: return .history
        case .settings: return .settings
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
            sampleTodayView
        case .history:
            sampleHistoryView
        case .settings:
            sampleSettingsView
        }
    }

    private var sampleTodayView: some View {
        VStack(alignment: .leading, spacing: 16) {
            PatientCard(accent: PatientUI.teal) {
                VStack(alignment: .leading, spacing: 16) {
                    Text(NSLocalizedString("patient.today.next.title", comment: "Next medication title"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.primary)

                    HStack(alignment: .center, spacing: 16) {
                        Image(systemName: "clock.fill")
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(PatientUI.tealDark)
                            .frame(width: 66, height: 66)
                            .background(PatientUI.teal.opacity(0.12), in: Circle())
                        VStack(alignment: .leading, spacing: 6) {
                            Text("昼のお薬")
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundStyle(PatientUI.tealDark)
                                .lineLimit(1)
                                .minimumScaleFactor(0.72)
                            Text("12:30")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(Color.readableSecondaryText)
                        }
                        Spacer(minLength: 0)
                    }

                    Text(String(
                        format: NSLocalizedString("patient.today.slot.bulk.summary", comment: "Summary"),
                        "2",
                        "2"
                    ))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.readableSecondaryText)

                    VStack(spacing: 10) {
                        sampleSlotMedicationRow(name: "血圧の薬 5 mg", pills: "1", status: .pending)
                        sampleSlotMedicationRow(name: "胃薬", pills: "1", status: .pending)
                    }

                    Label(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record button"), systemImage: "checkmark.circle.fill")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 72)
                        .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }

            PatientCard(accent: PatientUI.orange) {
                HStack(alignment: .center, spacing: 16) {
                    Image(systemName: "cross.case.fill")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(PatientUI.orange)
                        .frame(width: 64, height: 64)
                        .background(PatientUI.orange.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 7) {
                        Text(NSLocalizedString("patient.today.prn.entry.title", comment: "PRN entry title"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.82)
                        Text(String(format: NSLocalizedString("patient.today.prn.entry.message", comment: "PRN entry message"), 1))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Color.readableSecondaryText)
                }
            }

            HStack {
                Text(NSLocalizedString("patient.today.section.planned", comment: "Planned section"))
                    .font(.title2.weight(.bold))
                Spacer()
                PatientStatusPill(text: "1/3", color: PatientUI.blue)
            }

            sampleSlotCard(
                title: NSLocalizedString("patient.today.section.slot.morning", comment: "Morning slot"),
                time: "08:00",
                color: PatientUI.teal,
                status: NSLocalizedString("patient.today.status.taken", comment: "Taken"),
                statusColor: PatientUI.teal,
                remainingCount: nil,
                rows: [
                    ("整腸剤 50 mg", "1", SampleDoseStatus.taken)
                ],
                buttonEnabled: false
            )

            sampleSlotCard(
                title: NSLocalizedString("patient.today.section.slot.noon", comment: "Noon slot"),
                time: "12:30",
                color: PatientUI.blue,
                status: NSLocalizedString("patient.today.status.pending", comment: "Pending"),
                statusColor: .primary,
                remainingCount: 2,
                rows: [
                    ("血圧の薬 5 mg", "1", SampleDoseStatus.pending),
                    ("胃薬", "1", SampleDoseStatus.pending)
                ],
                buttonEnabled: true
            )
        }
    }

    private var sampleHistoryView: some View {
        VStack(alignment: .leading, spacing: 16) {
            PatientCard(accent: PatientUI.orange) {
                HStack(alignment: .center, spacing: 16) {
                    sampleProgressRing(value: "1/3", color: PatientUI.orange)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(NSLocalizedString("patient.history.today.progress.title", comment: "Today progress"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Color.readableSecondaryText)
                        Text(String(format: NSLocalizedString("patient.history.today.progress.format", comment: "Today progress format"), 1, 3))
                            .font(.title3.weight(.bold))
                            .foregroundStyle(.primary)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(NSLocalizedString("patient.history.today.encouragement.partial", comment: "Today encouragement"))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: 8) {
                                PatientStatusPill(text: String(format: NSLocalizedString("caregiver.history.summary.taken", comment: "Taken count"), 1), color: PatientUI.teal, systemImage: "checkmark.circle.fill")
                                PatientStatusPill(text: String(format: NSLocalizedString("patient.history.today.progress.remaining", comment: "Remaining count"), 2), color: PatientUI.orange, systemImage: "clock.fill")
                            }
                            VStack(alignment: .leading, spacing: 8) {
                                PatientStatusPill(text: String(format: NSLocalizedString("caregiver.history.summary.taken", comment: "Taken count"), 1), color: PatientUI.teal, systemImage: "checkmark.circle.fill")
                                PatientStatusPill(text: String(format: NSLocalizedString("patient.history.today.progress.remaining", comment: "Remaining count"), 2), color: PatientUI.orange, systemImage: "clock.fill")
                            }
                        }
                    }
                    Spacer(minLength: 0)
                }
            }

            PatientCard(accent: PatientUI.teal) {
                VStack(alignment: .center, spacing: 18) {
                    Text(NSLocalizedString("patient.history.week.title", comment: "Patient week title"))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)
                    VStack(spacing: 2) {
                        Text(String(format: NSLocalizedString("patient.history.week.count", comment: "Patient week count"), 3))
                            .font(.system(size: 50, weight: .bold, design: .rounded))
                            .foregroundStyle(PatientUI.teal)
                            .minimumScaleFactor(0.72)
                            .lineLimit(1)
                        Text(NSLocalizedString("patient.history.week.recorded", comment: "Patient week recorded"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(PatientUI.teal)
                    }
                    HStack(spacing: 8) {
                        sampleWeekDay("月", "6/8", color: PatientUI.teal, icon: "checkmark", filled: true)
                        sampleWeekDay("火", "6/9", color: PatientUI.teal, icon: "checkmark", filled: true)
                        sampleWeekDay("水", "6/10", color: PatientUI.orange, icon: "clock", filled: false)
                        sampleWeekDay("木", "6/11", color: PatientUI.blue, icon: "clock", filled: false)
                        sampleWeekDay("金", "6/12", color: Color.gray, icon: "minus", filled: false)
                    }
                    .frame(maxWidth: .infinity)
                    Text(NSLocalizedString("patient.history.week.encouragement.some", comment: "Patient week encouragement"))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            VStack(alignment: .leading, spacing: 12) {
                Text(NSLocalizedString("patient.history.recent.title", comment: "Recent records title"))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
                sampleRecentHistoryRow(title: "今日 6月11日（木）", subtitle: "朝・昼・夜のお薬", status: NSLocalizedString("patient.history.status.pending", comment: "Pending"), color: PatientUI.orange, icon: "sun.max.fill", statusIcon: nil)
                sampleRecentHistoryRow(title: "昨日 6月10日（水）", subtitle: "朝・昼のお薬", status: NSLocalizedString("patient.history.status.done", comment: "Done"), color: PatientUI.teal, icon: "checkmark.circle.fill", statusIcon: "checkmark")
            }
        }
    }

    private var sampleSettingsView: some View {
        VStack(alignment: .leading, spacing: 16) {
            PatientCard {
                VStack(alignment: .leading, spacing: 18) {
                    sampleSectionTitle(NSLocalizedString("patient.settings.notifications.card.title", comment: "Notification card title"), systemImage: "bell.badge.fill")
                    sampleLargeToggle(
                        title: NSLocalizedString("patient.settings.notifications.master", comment: "Enable notifications"),
                        subtitle: NSLocalizedString("patient.settings.notifications.master.note", comment: "Master note"),
                        systemImage: "bell.fill",
                        isOn: true
                    )
                }
            }

            PatientCard {
                sampleInfoRow(
                    title: NSLocalizedString("patient.settings.linked.title", comment: "Linked title"),
                    subtitle: NSLocalizedString("patient.settings.linked.note", comment: "Linked note"),
                    systemImage: "person.2.fill",
                    color: PatientUI.teal
                )
            }

            Label(NSLocalizedString("common.logout", comment: "Logout"), systemImage: "rectangle.portrait.and.arrow.right")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 58)
                .background(PatientUI.red, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }

    private var title: String {
        switch tab {
        case .today:
            return NSLocalizedString("patient.readonly.today.title", comment: "Today title")
        case .history:
            return NSLocalizedString("patient.readonly.history.title", comment: "History title")
        case .settings:
            return NSLocalizedString("patient.readonly.settings.title", comment: "Settings title")
        }
    }

    private var subtitle: String {
        switch tab {
        case .today:
            return sampleTodaySubtitle
        case .history:
            return NSLocalizedString("patient.history.subtitle", comment: "Patient history subtitle")
        case .settings:
            return NSLocalizedString("patient.settings.subtitle", comment: "Settings subtitle")
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

    private var sampleTodaySubtitle: String {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.japaneseLocale
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = AppConstants.defaultTimeZone
        formatter.dateFormat = "M月d日（E）"
        return formatter.string(from: Date())
    }

    private enum SampleDoseStatus {
        case taken
        case pending
        case missed
    }

    private func sampleSectionTitle(_ text: String, systemImage: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .foregroundStyle(PatientUI.teal)
            Text(text)
                .font(.headline.weight(.bold))
        }
    }

    private func sampleSlotCard(
        title: String,
        time: String,
        color: Color,
        status: String,
        statusColor: Color,
        remainingCount: Int?,
        rows: [(String, String, SampleDoseStatus)],
        buttonEnabled: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(color)
                    .frame(width: 16, height: 16)
                    .padding(.top, 7)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.title2.weight(.bold))
                    Text(time)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 8) {
                    Text(status)
                        .font(.subheadline.weight(.bold))
                        .padding(.vertical, 6)
                        .padding(.horizontal, 10)
                        .background(statusColor.opacity(0.15))
                        .clipShape(Capsule())
                    if let remainingCount {
                        Text(String(format: NSLocalizedString("patient.today.slot.bulk.remaining", comment: "Remaining"), remainingCount))
                            .font(.subheadline.weight(.bold))
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)
                            .background(Color.orange.opacity(0.16))
                            .clipShape(Capsule())
                    }
                }
            }

            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                sampleSlotMedicationRow(name: row.0, pills: row.1, status: row.2)
            }

            Text(String(
                format: NSLocalizedString("patient.today.slot.bulk.summary", comment: "Summary"),
                rows.map { Double($0.1) ?? 0 }.reduce(0, +).formatted(.number.precision(.fractionLength(0...1))),
                "\(rows.count)"
            ))
            .font(.body.weight(.semibold))
            .foregroundStyle(Color.readableSecondaryText)

            Label(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record"), systemImage: "checkmark.circle.fill")
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 70)
                .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .opacity(buttonEnabled ? 1 : 0.55)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .frame(width: 6)
                .padding(.vertical, 14)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(color.opacity(0.45), lineWidth: 1.5)
        }
        .shadow(color: PatientUI.cardShadow, radius: 12, y: 5)
    }

    private func sampleSlotMedicationRow(name: String, pills: String, status: SampleDoseStatus) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(name)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(status == .missed ? Color.red : Color.primary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                Text(String(
                    format: NSLocalizedString("patient.today.slot.bulk.perDose", comment: "Per dose"),
                    pills
                ))
                .font(.body)
                .foregroundStyle(Color.readableSecondaryText)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)
            Spacer()
            sampleDoseStatusIndicator(status)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private func sampleDoseStatusIndicator(_ status: SampleDoseStatus) -> some View {
        switch status {
        case .taken:
            Image(systemName: "checkmark.circle.fill")
                .font(.title2)
                .foregroundStyle(.green)
        case .missed:
            Image(systemName: "exclamationmark.circle.fill")
                .font(.title2)
                .foregroundStyle(.red)
        case .pending:
            Image(systemName: "circle")
                .font(.title2)
                .foregroundStyle(Color.readableSecondaryText)
        }
    }

    private func sampleProgressRing(value: String, color: Color) -> some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.16), lineWidth: 10)
            Circle()
                .trim(from: 0, to: 0.34)
                .stroke(color, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text(value)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.64)
                Text(NSLocalizedString("patient.history.today.progress.unit", comment: "Dose slot unit"))
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color.readableSecondaryText)
            }
        }
        .frame(width: 86, height: 86)
    }

    private func sampleWeekDay(_ weekday: String, _ date: String, color: Color, icon: String, filled: Bool) -> some View {
        VStack(spacing: 6) {
            Text(weekday)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
            ZStack {
                Circle()
                    .fill(color.opacity(filled ? 1 : 0.14))
                    .frame(width: 34, height: 34)
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(filled ? Color.white : color)
            }
            Text(date)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Color.readableSecondaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
    }

    private func sampleRecentHistoryRow(title: String, subtitle: String, status: String, color: Color, icon: String, statusIcon: String?) -> some View {
        PatientCard {
            HStack(alignment: .center, spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(color)
                    .frame(width: 54, height: 54)
                    .background(color.opacity(0.12), in: Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.76)
                    Text(subtitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                PatientStatusPill(text: status, color: color, systemImage: statusIcon)
            }
        }
    }

    private func sampleLargeToggle(title: String, subtitle: String, systemImage: String, isOn: Bool) -> some View {
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
                    .foregroundStyle(Color.readableSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Capsule()
                .fill(isOn ? PatientUI.teal : Color.readableSecondaryText.opacity(0.24))
                .frame(width: 52, height: 32)
                .overlay(alignment: isOn ? .trailing : .leading) {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 28, height: 28)
                        .padding(2)
                        .shadow(color: Color.black.opacity(0.12), radius: 2, y: 1)
                }
        }
    }

    private func sampleInfoRow(title: String, subtitle: String, systemImage: String, color: Color) -> some View {
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
                    .foregroundStyle(Color.readableSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Image(systemName: "checkmark.circle.fill")
                .font(.title2.weight(.bold))
                .foregroundStyle(PatientUI.teal)
                .accessibilityHidden(true)
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
    @ObservedObject private var analyticsService = AnalyticsService.shared
    @State private var showingLogoutConfirm = false
    @State private var selectedLegalDestination: PatientLegalWebDestination?
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

                PatientCard {
                    VStack(alignment: .leading, spacing: 18) {
                        settingsSectionTitle(
                            NSLocalizedString("analytics.settings.title", comment: "Analytics setting title"),
                            systemImage: "chart.bar.xaxis",
                            color: PatientUI.teal
                        )

                        largeToggle(
                            title: NSLocalizedString("analytics.settings.toggle", comment: "Analytics opt-in toggle"),
                            subtitle: NSLocalizedString("analytics.settings.detail", comment: "Analytics privacy detail"),
                            systemImage: "hand.raised.fill",
                            isOn: Binding(
                                get: { analyticsService.isEnabled },
                                set: { analyticsService.setCollectionEnabled($0) }
                            )
                        )
                        .accessibilityIdentifier("PatientAnalyticsCollectionToggle")
                    }
                }

                PatientCard {
                    VStack(alignment: .leading, spacing: 18) {
                        settingsSectionTitle(
                            NSLocalizedString("legal.section.title", comment: "Legal and support section title"),
                            systemImage: "doc.text.magnifyingglass",
                            color: PatientUI.teal
                        )

                        Button {
                            presentLegalURL(AppConstants.privacyPolicyURL)
                        } label: {
                            settingsNavigationRow(
                                title: NSLocalizedString("legal.privacy.title", comment: "Privacy policy title"),
                                subtitle: NSLocalizedString("legal.privacy.message", comment: "Privacy policy message"),
                                systemImage: "hand.raised.fill",
                                color: PatientUI.teal
                            )
                        }
                        .buttonStyle(.plain)

                        Button {
                            presentLegalURL(AppConstants.termsURL)
                        } label: {
                            settingsNavigationRow(
                                title: NSLocalizedString("legal.terms.title", comment: "Terms title"),
                                subtitle: NSLocalizedString("legal.terms.message", comment: "Terms message"),
                                systemImage: "doc.text.fill",
                                color: PatientUI.blue
                            )
                        }
                        .buttonStyle(.plain)

                        Button {
                            presentLegalURL(AppConstants.supportURL)
                        } label: {
                            settingsNavigationRow(
                                title: NSLocalizedString("legal.support.title", comment: "Support title"),
                                subtitle: NSLocalizedString("legal.support.message", comment: "Support message"),
                                systemImage: "questionmark.circle.fill",
                                color: PatientUI.orange
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                if notificationsDisabled {
                    PatientCard(accent: PatientUI.red) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(PatientUI.red)
                            Text(NSLocalizedString("patient.settings.notifications.permission.denied", comment: "Permission denied guidance"))
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Color.readableSecondaryText)
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
        .sheet(item: $selectedLegalDestination) { destination in
            SafariSheet(url: destination.url)
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
                        .foregroundStyle(Color.readableSecondaryText)
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
                    .foregroundStyle(Color.readableSecondaryText)
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
                    .foregroundStyle(Color.readableSecondaryText)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.headline.weight(.bold))
                .foregroundStyle(Color.readableSecondaryText)
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
            await scheduler.cancelAllScheduledNotifications()
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

    private func presentLegalURL(_ url: URL) {
        selectedLegalDestination = PatientLegalWebDestination(url: url)
    }
}

private struct PatientLegalWebDestination: Identifiable {
    let id = UUID()
    let url: URL
}

private struct SafariSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

enum PatientTab: Hashable {
    case today
    case history
    case settings
}

enum PatientUI {
    static let teal = AppTheme.primaryTeal
    static let tealDark = AppTheme.primaryTealText
    static let blue = AppTheme.blue
    static let orange = AppTheme.orange
    static let indigo = AppTheme.indigo
    static let red = AppTheme.patientRed
    static let backgroundTop = AppTheme.screenBackground
    static let backgroundBottom = backgroundTop
    static let cardBackground = AppTheme.cardBackground
    static let elevatedBackground = AppTheme.elevatedBackground
    static let cardStroke = AppTheme.cardStroke
    static let cardShadow = AppTheme.patientCardShadow
}

struct PatientScreenBackground: View {
    var body: some View {
        PatientUI.backgroundTop
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
                    Circle().stroke(PatientUI.cardBackground, lineWidth: 5)
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
                    .foregroundStyle(Color.readableSecondaryText)
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
            .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
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
        .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
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
            .foregroundStyle(isSelected ? PatientUI.teal : Color.readableSecondaryText)
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
                    .foregroundStyle(Color.readableSecondaryText)
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
