import FirebaseMessaging
import SwiftUI
import UIKit
import UserNotifications

enum CaregiverTab: Hashable {
    case medications
    case today
    case history
    case inventory
    case patients
}

struct CaregiverHomeView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var notificationRouter: NotificationDeepLinkRouter
    @State private var selectedTab: CaregiverTab = .today
    @State private var currentPatientName: String?
    @State private var hasAnyPatient: Bool?
    @State private var patientListErrorMessage: String?
    @State private var hasLowStock = false
    @State private var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var shouldOpenCreatePatient = false
    @State private var tutorialStepIndex: Int?
    var entitlementStore: EntitlementStore?

    var body: some View {
        ZStack {
            switch selectedTab {
            case .today:
                CaregiverTodayTabView(
                    sessionStore: sessionStore,
                    patientName: currentPatientName,
                    onOpenPatients: { openPatientSettings() },
                    onOpenMedications: { selectedTab = .medications },
                    onCreatePatient: { openPatientCreate() }
                )
            case .medications:
                CaregiverMedicationView(
                    sessionStore: sessionStore,
                    onOpenPatients: { openPatientSettings() },
                    onCreatePatient: { openPatientCreate() }
                )
            case .history:
                NavigationStack {
                    CaregiverHistoryView(
                        sessionStore: sessionStore,
                        entitlementStore: entitlementStore,
                        patientName: currentPatientName,
                        hasAnyPatient: hasAnyPatient,
                        patientListErrorMessage: patientListErrorMessage,
                        deepLinkTarget: $deepLinkTarget,
                        onRetryPatients: { loadCurrentPatientName() },
                        onOpenPatients: { openPatientSettings() },
                        onCreatePatient: { openPatientCreate() }
                    )
                }
            case .inventory:
                NavigationStack {
                    InventoryListView(
                        sessionStore: sessionStore,
                        onOpenPatients: { openPatientSettings() },
                        onOpenMedications: { selectedTab = .medications },
                        onCreatePatient: { openPatientCreate() },
                        hasAnyPatient: hasAnyPatient,
                        patientListErrorMessage: patientListErrorMessage,
                        onRetryPatients: { loadCurrentPatientName() },
                        patientName: currentPatientName
                    )
                }
            case .patients:
                PatientManagementView(
                    sessionStore: sessionStore,
                    entitlementStore: entitlementStore,
                    shouldOpenCreate: $shouldOpenCreatePatient
                )
            }

            if let tutorialStepIndex {
                CaregiverTutorialSampleView(sample: caregiverTutorialSteps[tutorialStepIndex].sample)
                    .zIndex(5)
                    .allowsHitTesting(false)

                if !isMarketingScreenshotPreview {
                    GuidedTutorialOverlay(
                        step: caregiverTutorialSteps[tutorialStepIndex].step,
                        stepIndex: tutorialStepIndex,
                        stepCount: caregiverTutorialSteps.count,
                        tint: CaregiverUI.orange,
                        skipTitle: isCurrentTutorialNotificationStep
                            ? NSLocalizedString("tutorial.notification.later", comment: "Set notification later")
                            : nil,
                        finalPrimaryTitle: isCurrentTutorialNotificationStep
                            ? NSLocalizedString("tutorial.notification.enable", comment: "Enable notification tutorial action")
                            : nil,
                        finalPrimarySystemImage: isCurrentTutorialNotificationStep ? "bell.badge.fill" : nil,
                        onSkip: { finishTutorial(openRegistration: false) },
                        onPrevious: { moveTutorial(by: -1) },
                        onNext: { handleTutorialNext() },
                        onFinish: { finishTutorial(openRegistration: true) }
                    )
                    .zIndex(10)
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            CaregiverBottomTabBar(
                selectedTab: $selectedTab,
                hasLowStock: hasLowStock,
                highlightedTab: currentTutorialTab
            )
            .padding(.horizontal, 12)
            .padding(.bottom, 4)
        }
        .onAppear {
            AnalyticsService.shared.logCaregiverTabViewed(analyticsTab(for: selectedTab))
            loadCurrentPatientName()
            checkLowStock()
            startTutorialIfNeeded()
            routeUITestRemotePushIfNeeded()
        }
        .onChange(of: sessionStore.currentPatientId) { _, _ in
            loadCurrentPatientName()
            checkLowStock()
        }
        .onChange(of: sessionStore.mode) { _, _ in
            loadCurrentPatientName()
            checkLowStock()
        }
        .onChange(of: selectedTab) { _, newTab in
            AnalyticsService.shared.logCaregiverTabViewed(analyticsTab(for: newTab))
            if newTab == .inventory || newTab == .medications || newTab == .today {
                checkLowStock()
            }
        }
        .onChange(of: tutorialStepIndex) { _, index in
            guard let index else { return }
            AnalyticsService.shared.logTutorialStepViewed(mode: .caregiver, step: index + 1)
            selectedTab = caregiverTutorialSteps[index].tab
        }
        .onReceive(NotificationCenter.default.publisher(for: .medicationUpdated)) { _ in
            checkLowStock()
        }
        .onChange(of: sessionStore.shouldRedirectCaregiverToMedicationTab) { _, shouldRedirect in
            guard shouldRedirect else { return }
            selectedTab = .medications
            sessionStore.shouldRedirectCaregiverToMedicationTab = false
        }
        .onReceive(notificationRouter.$target) { newTarget in
            guard let target = newTarget,
                  sessionStore.mode == .caregiver else { return }
            selectedTab = .history
            deepLinkTarget = target
            notificationRouter.clear()
        }
    }

    private var caregiverTutorialSteps: [CaregiverTutorialStep] {
        [
            CaregiverTutorialStep(
                tab: .today,
                sample: .tab(.today),
                step: GuidedTutorialStep(
                    id: "caregiver-today",
                    icon: "house.fill",
                    title: NSLocalizedString("tutorial.caregiver.today.title", comment: "Caregiver today tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.today.message", comment: "Caregiver today tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .medications,
                sample: .tab(.medications),
                step: GuidedTutorialStep(
                    id: "caregiver-medications",
                    icon: "pills.fill",
                    title: NSLocalizedString("tutorial.caregiver.medications.title", comment: "Caregiver medications tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.medications.message", comment: "Caregiver medications tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .inventory,
                sample: .tab(.inventory),
                step: GuidedTutorialStep(
                    id: "caregiver-inventory",
                    icon: "shippingbox.fill",
                    title: NSLocalizedString("tutorial.caregiver.inventory.title", comment: "Caregiver inventory tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.inventory.message", comment: "Caregiver inventory tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .history,
                sample: .tab(.history),
                step: GuidedTutorialStep(
                    id: "caregiver-history",
                    icon: "clock.fill",
                    title: NSLocalizedString("tutorial.caregiver.history.title", comment: "Caregiver history tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.history.message", comment: "Caregiver history tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .patients,
                sample: .tab(.patients),
                step: GuidedTutorialStep(
                    id: "caregiver-settings",
                    icon: "gearshape.fill",
                    title: NSLocalizedString("tutorial.caregiver.settings.title", comment: "Caregiver settings tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.settings.message", comment: "Caregiver settings tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .patients,
                sample: .timePreset,
                step: GuidedTutorialStep(
                    id: "caregiver-time-preset",
                    icon: "clock.badge.checkmark.fill",
                    title: NSLocalizedString("tutorial.caregiver.timePreset.title", comment: "Caregiver time preset tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.timePreset.message", comment: "Caregiver time preset tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .patients,
                sample: .registerPatient,
                step: GuidedTutorialStep(
                    id: "caregiver-register-patient",
                    icon: "person.badge.plus.fill",
                    title: NSLocalizedString("tutorial.caregiver.register.title", comment: "Caregiver register tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.register.message", comment: "Caregiver register tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .patients,
                sample: .issueCode,
                step: GuidedTutorialStep(
                    id: "caregiver-issue-code",
                    icon: "link.badge.plus",
                    title: NSLocalizedString("tutorial.caregiver.issueCode.title", comment: "Caregiver issue code tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.issueCode.message", comment: "Caregiver issue code tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .patients,
                sample: .shareCode,
                step: GuidedTutorialStep(
                    id: "caregiver-share-code",
                    icon: "square.and.arrow.up",
                    title: NSLocalizedString("tutorial.caregiver.shareCode.title", comment: "Caregiver share code tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.shareCode.message", comment: "Caregiver share code tutorial message")
                )
            ),
            CaregiverTutorialStep(
                tab: .today,
                sample: .notificationPermission,
                step: GuidedTutorialStep(
                    id: "caregiver-notification-permission",
                    icon: "bell.badge.fill",
                    title: NSLocalizedString("tutorial.caregiver.notification.title", comment: "Caregiver notification permission tutorial title"),
                    message: NSLocalizedString("tutorial.caregiver.notification.message", comment: "Caregiver notification permission tutorial message")
                )
            )
        ]
    }

    private var currentTutorialTab: CaregiverTab? {
        guard let tutorialStepIndex else { return nil }
        return caregiverTutorialSteps[tutorialStepIndex].tab
    }

    private var isCurrentTutorialNotificationStep: Bool {
        guard let tutorialStepIndex else { return false }
        return caregiverTutorialSteps[tutorialStepIndex].step.id == "caregiver-notification-permission"
    }

    private var isMarketingScreenshotPreview: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains { $0.hasPrefix("-CaregiverMarketingScreenshot.") }
        #else
        false
        #endif
    }

    // MARK: - Data Loading

    private func loadCurrentPatientName() {
        guard sessionStore.mode == .caregiver else {
            currentPatientName = nil
            hasAnyPatient = nil
            patientListErrorMessage = nil
            return
        }
        Task { @MainActor in
            do {
                let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
                let patients = try await apiClient.listPatients()
                patientListErrorMessage = nil
                hasAnyPatient = !patients.isEmpty
                let selectedPatient = patients.first { $0.id == sessionStore.currentPatientId }
                if let selectedPatient {
                    currentPatientName = selectedPatient.displayName
                } else {
                    if sessionStore.currentPatientId != nil {
                        sessionStore.clearCurrentPatientId()
                    }
                    if patients.count == 1, let onlyPatient = patients.first {
                        sessionStore.setCurrentPatientId(onlyPatient.id)
                        currentPatientName = onlyPatient.displayName
                    } else {
                        currentPatientName = nil
                    }
                }
            } catch {
                currentPatientName = nil
                hasAnyPatient = nil
                patientListErrorMessage = NSLocalizedString(
                    "caregiver.dataUnavailable.message",
                    comment: "Caregiver data unavailable message"
                )
            }
        }
    }

    private func checkLowStock() {
        guard sessionStore.mode == .caregiver,
              sessionStore.currentPatientId != nil else {
            hasLowStock = false
            return
        }
        Task { @MainActor in
            do {
                let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
                let items = try await apiClient.fetchInventory()
                hasLowStock = items.contains { $0.inventoryEnabled && ($0.low || $0.out) }
            } catch {
                // Keep the current value on error
            }
        }
    }

    private func openPatientSettings() {
        selectedTab = .patients
    }

    private func openPatientCreate() {
        shouldOpenCreatePatient = true
        selectedTab = .patients
    }

    private func routeUITestRemotePushIfNeeded() {
        #if DEBUG
        let env = ProcessInfo.processInfo.environment
        guard env["UITEST_MOCK_PUSH"] == "1",
              let date = env["UITEST_REMOTE_PUSH_DATE"],
              let slot = env["UITEST_REMOTE_PUSH_SLOT"],
              NotificationSlot(rawValue: slot) != nil else {
            return
        }
        notificationRouter.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": date,
            "slot": slot
        ])
        #endif
    }

    private func startTutorialIfNeeded() {
        #if DEBUG
        if let argument = ProcessInfo.processInfo.arguments.first(where: {
            $0.hasPrefix("-CaregiverMarketingScreenshot.")
        }) {
            let requestedTab = String(argument.dropFirst("-CaregiverMarketingScreenshot.".count))
            let index: Int
            switch requestedTab {
            case "medications": index = 1
            case "inventory": index = 2
            case "history": index = 3
            default: index = 0
            }
            tutorialStepIndex = index
            selectedTab = caregiverTutorialSteps[index].tab
            return
        }
        if ProcessInfo.processInfo.arguments.contains("-CaregiverNotificationTutorialPreview") {
            let index = caregiverTutorialSteps.count - 1
            tutorialStepIndex = index
            selectedTab = caregiverTutorialSteps[index].tab
            return
        }
        #endif
        guard sessionStore.shouldShowModeTutorial(for: .caregiver)
            || sessionStore.shouldForceModeTutorial(for: .caregiver) else { return }
        tutorialStepIndex = 0
        AnalyticsService.shared.logTutorialStarted(mode: .caregiver)
        selectedTab = caregiverTutorialSteps[0].tab
    }

    private func moveTutorial(by offset: Int) {
        guard let tutorialStepIndex else { return }
        let nextIndex = tutorialStepIndex + offset
        guard caregiverTutorialSteps.indices.contains(nextIndex) else {
            finishTutorial(openRegistration: true)
            return
        }
        withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) {
            self.tutorialStepIndex = nextIndex
        }
    }

    private func handleTutorialNext() {
        if isCurrentTutorialNotificationStep {
            Task { await enablePushNotificationsFromTutorial() }
        } else {
            moveTutorial(by: 1)
        }
    }

    private func enablePushNotificationsFromTutorial() async {
        let granted = await requestNotificationAuthorization()
        if granted {
            UIApplication.shared.registerForRemoteNotifications()
            await registerCaregiverPushDeviceIfPossible()
        }
        finishTutorial(openRegistration: true)
    }

    private func requestNotificationAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                continuation.resume(returning: granted)
            }
        }
    }

    private func registerCaregiverPushDeviceIfPossible() async {
        do {
            let token = try await retrieveFCMTokenWithRetry()
            let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
            try await apiClient.registerPushDevice(
                token: token,
                platform: "ios",
                environment: DeviceTokenManager.pushEnvironment
            )
            UserDefaults.standard.set(true, forKey: CaregiverPushSettingsViewModel.persistKey)
        } catch {
            print("CaregiverHomeView: tutorial push registration failed: \(error.localizedDescription)")
        }
    }

    private func retrieveFCMTokenWithRetry() async throws -> String {
        var lastError: Error?
        for attempt in 0..<4 {
            do {
                return try await Messaging.messaging().token()
            } catch {
                lastError = error
                if attempt < 3 {
                    try await Task.sleep(nanoseconds: 750_000_000)
                }
            }
        }
        throw lastError ?? DeviceTokenError.noFCMToken
    }

    private func finishTutorial(openRegistration: Bool = false) {
        AnalyticsService.shared.logTutorialFinished(mode: .caregiver, skipped: !openRegistration)
        sessionStore.markModeTutorialSeen(for: .caregiver)
        withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
            tutorialStepIndex = nil
        }
        if openRegistration {
            selectedTab = .patients
            shouldOpenCreatePatient = true
        }
    }

    private func analyticsTab(for tab: CaregiverTab) -> AnalyticsCaregiverTab {
        switch tab {
        case .today: return .today
        case .medications: return .medications
        case .history: return .history
        case .inventory: return .inventory
        case .patients: return .settings
        }
    }
}

private struct CaregiverTutorialStep {
    let tab: CaregiverTab
    let sample: CaregiverTutorialSample
    let step: GuidedTutorialStep
}

private enum CaregiverTutorialSample {
    case tab(CaregiverTab)
    case timePreset
    case registerPatient
    case issueCode
    case shareCode
    case notificationPermission

    var tab: CaregiverTab {
        switch self {
        case .tab(let tab):
            return tab
        case .timePreset, .registerPatient, .issueCode, .shareCode:
            return .patients
        case .notificationPermission:
            return .today
        }
    }
}

private struct CaregiverTutorialSampleView: View {
    let sample: CaregiverTutorialSample

    private var tab: CaregiverTab {
        sample.tab
    }

    var body: some View {
        CaregiverScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if tab == .today {
                        todayHeader
                    } else {
                        header
                    }
                    content
                }
                .padding(.horizontal, tab == .medications || tab == .inventory ? 16 : 20)
                .padding(.top, 16)
                .padding(.bottom, 260)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(CaregiverUI.background)
        .ignoresSafeArea(edges: .bottom)
        .transition(.opacity)
    }

    private var header: some View {
        CaregiverPatientHeader(
            title: title,
            patientName: "田中 花子",
            systemImage: icon,
            subtitle: subtitle
        )
    }

    @ViewBuilder
    private var content: some View {
        switch sample {
        case .timePreset:
            sampleSettingsSelectionCard()
            sampleSettingsDetailCard()
        case .registerPatient:
            samplePatientRegistrationCard()
        case .issueCode:
            sampleSettingsSelectionCard()
            sampleSettingsPatientCard(highlightIssueCode: true)
        case .shareCode:
            sampleLinkCodeCard()
        case .notificationPermission:
            sampleNotificationPermissionCard()
        case .tab:
            tabContent
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch tab {
        case .today:
            CaregiverCard(accent: CaregiverUI.orange) {
                VStack(alignment: .leading, spacing: 14) {
                    Text(NSLocalizedString("caregiver.today.nextAction.title", comment: "Next action title"))
                        .font(.headline.weight(.bold))
                    HStack(alignment: .center, spacing: 16) {
                        Image(systemName: "clock")
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(CaregiverUI.tealDark)
                            .frame(width: 66, height: 66)
                            .background(CaregiverUI.tealDark.opacity(0.10), in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text("次に記録する時間")
                                .font(.headline.weight(.bold))
                            Text("昼 12:30")
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundStyle(CaregiverUI.tealDark)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        Spacer()
                    }
                    HStack(spacing: 10) {
                        CaregiverStatusPill(text: "未記録", color: CaregiverUI.orange)
                        Text("この時間帯の未記録2件をまとめて記録します")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                    }
                    Text(NSLocalizedString("caregiver.today.nextAction.medicinesTitle", comment: "Medicines title"))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Color.readableSecondaryText)
                    sampleCompactDoseLine(name: "血圧の薬 5 mg", detail: "1回1錠", color: CaregiverUI.teal)
                    sampleCompactDoseLine(name: "胃薬", detail: "1回1錠", color: CaregiverUI.blue)
                    samplePrimaryButton(title: NSLocalizedString("caregiver.today.primaryRecord.slot", comment: "Record slot"), systemImage: "pills.fill", color: CaregiverUI.teal)
                }
            }
            CaregiverCard {
                HStack(spacing: 16) {
                    sampleProgressRing()
                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("caregiver.today.progress.title", comment: "Progress title"))
                            .font(.headline.weight(.bold))
                        Text("2/3回分 記録済み")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(CaregiverUI.tealDark)
                        Text("次は昼のお薬です")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                    }
                    Spacer(minLength: 0)
                }
            }
        case .medications:
            sampleMedicationMetrics()
            sampleFilterChips(items: [
                ("すべて", "list.bullet", CaregiverUI.teal, true),
                ("定時", "clock.fill", CaregiverUI.blue, false),
                ("頓服", "cross.case.fill", CaregiverUI.orange, false),
                ("終了", "calendar.badge.clock", .gray, false)
            ])
            VStack(alignment: .leading, spacing: 8) {
                sectionHeader("定時")
                sampleMedicationListRow(name: "血圧の薬 5 mg", badge: "定時", detail: "毎日 朝・昼", dose: "1回1錠", inventory: "残り18錠", color: CaregiverUI.blue)
                sampleMedicationListRow(name: "整腸剤 50 mg", badge: "定時", detail: "毎日 夜", dose: "1回1錠", inventory: "残り10錠", color: CaregiverUI.teal)
                sectionHeader("頓服")
                sampleMedicationListRow(name: "頭痛薬", badge: "頓服", detail: "必要な時", dose: "1回1錠", inventory: nil, color: CaregiverUI.orange)
            }
        case .inventory:
            sampleInventoryMetrics()
            CaregiverCard {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "bell.badge.fill")
                        .font(.title2)
                        .foregroundStyle(CaregiverUI.orange)
                        .frame(width: 34, height: 34)
                        .background(CaregiverUI.orange.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("caregiver.inventory.guide.action.title", comment: "Inventory guide action title"))
                            .font(.headline.weight(.bold))
                        Text("血圧の薬 5 mg が残り少なくなっています。補充したら在庫数を更新してください。")
                            .font(.subheadline)
                            .foregroundStyle(Color.readableSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            sampleFilterChips(items: [
                ("すべて", "list.bullet", CaregiverUI.teal, true),
                ("低在庫のみ", "exclamationmark.triangle.fill", CaregiverUI.orange, false),
                ("在庫なし", "xmark.circle.fill", CaregiverUI.red, false)
            ])
            sectionHeader("在庫一覧")
            sampleInventoryListRow(name: "血圧の薬 5 mg", quantity: "4", unit: "錠", days: "あと2日分", help: "残り日数が少ないため、早めの補充が必要です。", color: CaregiverUI.orange, attention: true)
            sampleInventoryListRow(name: "整腸剤 50 mg", quantity: "10", unit: "錠", days: "あと5日分", help: "服薬記録に合わせて自動で減ります。", color: CaregiverUI.teal, attention: false)
        case .history:
            sampleHistoryCalendarCard()
            sampleHistorySelectedDayCard()
        case .patients:
            sampleSettingsSelectionCard()
            sampleSettingsPatientCard()
            sampleSettingsDetailCard()
            sampleSettingsPushCard()
        }
    }

    private var todayHeader: some View {
        HStack(alignment: .center, spacing: 12) {
            CaregiverAvatar(name: "田中 花子", systemImage: "person.crop.circle.fill")
                .frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 3) {
                Text("田中 花子さん")
                    .font(.title3.weight(.bold))
                Text(NSLocalizedString("caregiver.today.title", comment: "Caregiver today title"))
                    .font(.title2.weight(.bold))
            }
            Spacer()
        }
    }

    private var title: String {
        switch sample {
        case .registerPatient:
            return "見守る方を登録"
        case .issueCode:
            return "連携コードを発行"
        case .shareCode:
            return "本人へコードを共有"
        case .timePreset:
            return "服用時間を調整"
        case .notificationPermission:
            return "通知を受け取る"
        case .tab:
            break
        }
        switch tab {
        case .today:
            return "今日の予定"
        case .medications:
            return "薬を管理"
        case .inventory:
            return "在庫を確認"
        case .history:
            return "服薬履歴"
        case .patients:
            return "連携・設定"
        }
    }

    private var subtitle: String {
        switch sample {
        case .registerPatient:
            return "最初に本人の名前を登録します"
        case .issueCode:
            return "登録後に本人用のコードを作ります"
        case .shareCode:
            return "コピーまたは共有で本人へ渡します"
        case .timePreset:
            return "朝・昼・夜・眠前の時刻を変更できます"
        case .notificationPermission:
            return "服薬記録や飲み忘れを通知します"
        case .tab:
            break
        }
        switch tab {
        case .today:
            return "このように今日飲む予定がまとまります"
        case .medications:
            return "登録した薬が一覧で表示されます"
        case .inventory:
            return "残数と補充目安を確認できます"
        case .history:
            return "記録状況を日付ごとに確認できます"
        case .patients:
            return "見守る方と連携状態を管理できます"
        }
    }

    private var icon: String {
        switch sample {
        case .registerPatient:
            return "person.badge.plus.fill"
        case .issueCode:
            return "link.badge.plus"
        case .shareCode:
            return "square.and.arrow.up"
        case .timePreset:
            return "clock.badge.checkmark.fill"
        case .notificationPermission:
            return "bell.badge.fill"
        case .tab:
            break
        }
        switch tab {
        case .today:
            return "house.fill"
        case .medications:
            return "pills.fill"
        case .inventory:
            return "shippingbox.fill"
        case .history:
            return "clock.fill"
        case .patients:
            return "gearshape.fill"
        }
    }

    private func sampleNotificationPermissionCard() -> some View {
        CaregiverCard(accent: CaregiverUI.orange) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center, spacing: 14) {
                    Image(systemName: "bell.badge.fill")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(CaregiverUI.orange)
                        .frame(width: 52, height: 52)
                        .background(CaregiverUI.orange.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 5) {
                        Text(NSLocalizedString("tutorial.caregiver.notification.title", comment: "Caregiver notification permission tutorial title"))
                            .font(.title3.weight(.bold))
                        Text("本人が記録したときにこの端末へ知らせます。")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                VStack(alignment: .leading, spacing: 10) {
                    sampleNotificationBenefit(
                        icon: "checkmark.circle.fill",
                        text: "服薬記録をすぐ確認できます",
                        color: CaregiverUI.teal
                    )
                    sampleNotificationBenefit(
                        icon: "exclamationmark.triangle.fill",
                        text: "飲み忘れに気づきやすくなります",
                        color: CaregiverUI.red
                    )
                }
            }
        }
    }

    private func sampleNotificationBenefit(icon: String, text: String, color: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.headline.weight(.bold))
                .foregroundStyle(color)
                .frame(width: 28, height: 28)
                .background(color.opacity(0.10), in: Circle())
            Text(text)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Spacer(minLength: 0)
        }
    }

    private func samplePatientRegistrationCard() -> some View {
        CaregiverCard(accent: CaregiverUI.orange) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Image(systemName: "person.badge.plus.fill")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(CaregiverUI.orange)
                        .frame(width: 48, height: 48)
                        .background(CaregiverUI.orange.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("caregiver.patients.create.title", comment: "Create title"))
                            .font(.title3.weight(.bold))
                        Text("本人の名前を入力して保存します。")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(NSLocalizedString("caregiver.patients.create.section", comment: "Create section"))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Color.readableSecondaryText)
                    HStack(spacing: 12) {
                        Image(systemName: "person.fill")
                            .foregroundStyle(CaregiverUI.teal)
                        Text("田中 花子")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.primary)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 52)
                    .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }

                samplePrimaryButton(
                    title: NSLocalizedString("common.save", comment: "Save"),
                    systemImage: "checkmark",
                    color: CaregiverUI.orange
                )
            }
        }
    }

    private func sampleSettingsSelectionCard() -> some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.badge.checkmark")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(CaregiverUI.teal)
                        .frame(width: 34, height: 34)
                        .background(CaregiverUI.teal.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("caregiver.settings.patient.title", comment: "Settings patient title"))
                            .font(.headline.weight(.bold))
                        Text(NSLocalizedString("caregiver.patients.select.help", comment: "Select help text"))
                            .font(.subheadline)
                            .foregroundStyle(Color.readableSecondaryText)
                    }
                }

                HStack {
                    Text("田中 花子")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(CaregiverUI.tealDark)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(CaregiverUI.teal)
                }
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(CaregiverUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
    }

    private func sampleSettingsPatientCard(highlightIssueCode: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                HStack(spacing: 10) {
                    Image(systemName: "person.circle.fill")
                        .font(.title2)
                        .foregroundStyle(CaregiverUI.teal)
                    Text("田中 花子")
                        .font(.title3.weight(.semibold))
                }
                Spacer()
                Text(NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label"))
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(CaregiverUI.teal.opacity(0.18))
                    .foregroundStyle(CaregiverUI.teal)
                    .clipShape(Capsule())
            }
            sampleSettingsButton(
                title: NSLocalizedString("caregiver.patients.issueCode", comment: "Issue code"),
                systemImage: "link.badge.plus",
                tint: CaregiverUI.teal,
                isHighlighted: highlightIssueCode
            )
            sampleSettingsButton(
                title: NSLocalizedString("caregiver.patients.delete", comment: "Delete patient"),
                systemImage: "trash",
                tint: CaregiverUI.red
            )
        }
        .padding(18)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(CaregiverUI.teal.opacity(0.24), lineWidth: 1))
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
    }

    private func sampleLinkCodeCard() -> some View {
        CaregiverCard(accent: CaregiverUI.teal) {
            VStack(spacing: 18) {
                VStack(spacing: 8) {
                    Image(systemName: "link.badge.plus")
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundStyle(CaregiverUI.teal)
                    Text(NSLocalizedString("caregiver.patients.code.title", comment: "Linking code title"))
                        .font(.title3.weight(.bold))
                    Text(NSLocalizedString("caregiver.patients.code.subtitle", comment: "Code subtitle"))
                        .font(.subheadline)
                        .foregroundStyle(Color.readableSecondaryText)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 8) {
                    ForEach(Array("482913"), id: \.self) { char in
                        Text(String(char))
                            .font(.title2.weight(.bold).monospacedDigit())
                            .frame(width: 38, height: 50)
                            .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }

                HStack(spacing: 12) {
                    sampleSettingsButton(
                        title: NSLocalizedString("caregiver.patients.code.copy", comment: "Copy code"),
                        systemImage: "doc.on.doc",
                        tint: CaregiverUI.teal
                    )
                    sampleSettingsButton(
                        title: NSLocalizedString("caregiver.patients.code.share", comment: "Share code"),
                        systemImage: "square.and.arrow.up",
                        tint: CaregiverUI.orange,
                        isHighlighted: true
                    )
                }

                HStack(spacing: 6) {
                    Image(systemName: "clock")
                    Text("有効期限: 今日 18:00")
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.readableSecondaryText)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func sampleSettingsDetailCard() -> some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 14) {
                sampleSettingsGroupHeader(
                    title: NSLocalizedString("caregiver.settings.section.detail", comment: "Detail settings header"),
                    message: NSLocalizedString("caregiver.settings.detail.message", comment: "Detail settings message"),
                    systemImage: "slider.horizontal.3"
                )
                sampleSettingsActionRow(
                    title: NSLocalizedString("patient.settings.notifications.detail.item", comment: "Detail settings item"),
                    message: NSLocalizedString("patient.settings.notifications.detail.note", comment: "Detail settings note"),
                    systemImage: "clock.fill",
                    tint: CaregiverUI.blue
                )
            }
        }
    }

    private func sampleSettingsPushCard() -> some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 12) {
                sampleSettingsGroupHeader(
                    title: NSLocalizedString("caregiver.settings.push.section.title", comment: "Push section title"),
                    message: NSLocalizedString("caregiver.settings.push.enabled", comment: "Push enabled"),
                    systemImage: "bell.fill"
                )
                HStack {
                    Text(NSLocalizedString("caregiver.settings.push.toggle", comment: "Push toggle"))
                        .font(.body.weight(.semibold))
                    Spacer()
                    Capsule()
                        .fill(CaregiverUI.teal)
                        .frame(width: 52, height: 32)
                        .overlay(alignment: .trailing) {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 28, height: 28)
                                .padding(.trailing, 2)
                        }
                }
            }
        }
    }

    private func sampleSettingsGroupHeader(title: String, message: String, systemImage: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .font(.headline.weight(.bold))
                .foregroundStyle(CaregiverUI.teal)
                .frame(width: 34, height: 34)
                .background(CaregiverUI.teal.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline.weight(.bold))
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(Color.readableSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func sampleSettingsActionRow(title: String, message: String, systemImage: String, tint: Color) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: systemImage)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(tint)
                .frame(width: 32, height: 32)
                .background(tint.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                Text(message)
                    .font(.caption)
                    .foregroundStyle(Color.readableSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.readableSecondaryText)
        }
    }

    private func sampleSettingsButton(
        title: String,
        systemImage: String,
        tint: Color,
        isHighlighted: Bool = false
    ) -> some View {
        Label(title, systemImage: systemImage)
            .font(.subheadline.weight(.semibold))
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(tint.opacity(0.15))
            .foregroundStyle(tint)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay {
                if isHighlighted {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(tint, lineWidth: 3)
                }
            }
    }

    private func sampleSectionTitle(_ text: String, systemImage: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .foregroundStyle(CaregiverUI.teal)
            Text(text)
                .font(.headline.weight(.bold))
        }
    }

    private func sampleMedicineRow(name: String, detail: String, color: Color) -> some View {
        HStack(spacing: 12) {
            MedicationSymbolView(tint: color)
                .frame(width: 42, height: 42)
            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .font(.headline.weight(.bold))
                Text(detail)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.readableSecondaryText)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func sampleHistoryCalendarCard() -> some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(NSLocalizedString("history.calendar.title", comment: "History calendar title"))
                        .font(.headline.weight(.bold))
                    Text(NSLocalizedString("history.calendar.message", comment: "History calendar message"))
                        .font(.subheadline)
                        .foregroundStyle(Color.readableSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                sampleHistoryCalendarGrid()
                sampleHistoryLegend()
            }
        }
    }

    private func sampleHistoryCalendarGrid() -> some View {
        let weekdays = ["月", "火", "水", "木", "金", "土", "日"]
        let days: [Int?] = Array(1...30).map(Optional.some)
        return VStack(spacing: 10) {
            HStack(spacing: 0) {
                ForEach(weekdays, id: \.self) { symbol in
                    Text(symbol)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                        .frame(maxWidth: .infinity)
                }
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 8) {
                ForEach(days.indices, id: \.self) { index in
                    if let day = days[index] {
                        sampleHistoryDayCell(day: day)
                    } else {
                        Color.clear.frame(height: 54)
                    }
                }
            }
        }
    }

    private func sampleHistoryDayCell(day: Int) -> some View {
        let selected = day == 10
        let statuses = sampleHistoryStatuses(for: day)
        return VStack(spacing: 6) {
            Text("\(day)")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(selected ? Color.white : (statuses.isEmpty ? Color.readableSecondaryText : Color.primary))
                .frame(maxWidth: .infinity)
            HStack(spacing: 4) {
                ForEach(statuses.indices, id: \.self) { index in
                    Circle()
                        .fill(statuses[index])
                        .frame(width: 6, height: 6)
                }
            }
            .frame(height: 8)
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, minHeight: 54)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(selected ? CaregiverUI.teal : (statuses.isEmpty ? Color.clear : Color.primary.opacity(0.05)))
        )
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(selected ? CaregiverUI.tealDark.opacity(0.40) : Color.primary.opacity(statuses.isEmpty ? 0.04 : 0.08), lineWidth: 1)
        }
    }

    private func sampleHistoryStatuses(for day: Int) -> [Color] {
        switch day {
        case 5:
            return [CaregiverUI.teal, CaregiverUI.teal, CaregiverUI.teal]
        case 6:
            return [CaregiverUI.teal, Color.gray]
        case 8:
            return [CaregiverUI.teal, CaregiverUI.red]
        case 9:
            return [CaregiverUI.teal, CaregiverUI.teal, Color.purple]
        case 10:
            return [CaregiverUI.teal, Color.gray, CaregiverUI.red]
        case 11:
            return [Color.gray]
        default:
            return []
        }
    }

    private func sampleHistoryLegend() -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(NSLocalizedString("history.legend.help", comment: "Legend help"))
                .font(.caption)
                .foregroundStyle(Color.readableSecondaryText)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], alignment: .leading, spacing: 8) {
                sampleHistoryLegendItem(color: CaregiverUI.teal, title: NSLocalizedString("history.legend.taken", comment: "Legend taken"))
                sampleHistoryLegendItem(color: CaregiverUI.red, title: NSLocalizedString("history.legend.missed", comment: "Legend missed"))
                sampleHistoryLegendItem(color: Color.gray, title: NSLocalizedString("history.legend.pending", comment: "Legend pending"))
                sampleHistoryLegendItem(color: Color.purple, title: NSLocalizedString("history.legend.prn", comment: "Legend PRN"))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func sampleHistoryLegendItem(color: Color, title: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.readableSecondaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
    }

    private func sampleHistorySelectedDayCard() -> some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("history.selected.title", comment: "Selected day section title"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Color.readableSecondaryText)
                        Text("6月10日（水）")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(.primary)
                    }
                    Spacer()
                }
                Text(String(format: NSLocalizedString("caregiver.history.summary.format", comment: "History summary"), 1, 3))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
                Text(NSLocalizedString("history.selected.missedHelp", comment: "Missed help"))
                    .font(.subheadline)
                    .foregroundStyle(Color.readableSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    CaregiverStatusPill(
                        text: String(format: NSLocalizedString("caregiver.history.summary.taken", comment: "Taken count"), 1),
                        color: CaregiverUI.teal,
                        systemImage: "checkmark.circle.fill"
                    )
                    CaregiverStatusPill(
                        text: String(format: NSLocalizedString("caregiver.history.summary.pending", comment: "Pending count"), 1),
                        color: .gray,
                        systemImage: "clock.fill"
                    )
                    CaregiverStatusPill(
                        text: String(format: NSLocalizedString("caregiver.history.summary.missed", comment: "Missed count"), 1),
                        color: CaregiverUI.red,
                        systemImage: "exclamationmark.triangle.fill"
                    )
                }
            }
        }
    }

    private func sampleMetric(value: String, label: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.title.weight(.bold))
                .foregroundStyle(color)
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.readableSecondaryText)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func sampleCompactDoseLine(name: String, detail: String, color: Color) -> some View {
        HStack(spacing: 10) {
            MedicationSymbolView(tint: color)
                .frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.subheadline.weight(.bold))
                Text(detail)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.readableSecondaryText)
            }
            Spacer(minLength: 0)
        }
    }

    private func samplePrimaryButton(title: String, systemImage: String, color: Color) -> some View {
        Label(title, systemImage: systemImage)
            .font(.headline.weight(.bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(color, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func sampleProgressRing() -> some View {
        ZStack {
            Circle()
                .stroke(CaregiverUI.teal.opacity(0.16), lineWidth: 9)
            Circle()
                .trim(from: 0, to: 0.67)
                .stroke(CaregiverUI.teal, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text("2/3")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(CaregiverUI.tealDark)
        }
        .frame(width: 76, height: 76)
    }

    private func sampleMedicationMetrics() -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            sampleTile(title: NSLocalizedString("medication.list.metric.total", comment: "Total"), value: "3", tint: CaregiverUI.teal, systemImage: "pills.fill")
            sampleTile(title: NSLocalizedString("medication.list.metric.today", comment: "Scheduled"), value: "2", tint: CaregiverUI.blue, systemImage: "clock.fill")
            sampleTile(title: NSLocalizedString("medication.list.metric.prn", comment: "PRN"), value: "1", tint: CaregiverUI.orange, systemImage: "cross.case.fill")
            sampleTile(title: NSLocalizedString("medication.list.metric.ended", comment: "Ended"), value: "0", tint: .gray, systemImage: "calendar.badge.clock")
        }
    }

    private func sampleInventoryMetrics() -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            sampleTile(title: NSLocalizedString("caregiver.inventory.summary.needsAction", comment: "Needs action"), value: "1", tint: CaregiverUI.orange, systemImage: "exclamationmark.triangle.fill")
            sampleTile(title: NSLocalizedString("caregiver.inventory.summary.managed", comment: "Managed"), value: "2", tint: CaregiverUI.blue, systemImage: "archivebox.fill")
            sampleTile(title: NSLocalizedString("caregiver.inventory.summary.notStarted", comment: "Not started"), value: "1", tint: .gray, systemImage: "questionmark.circle.fill")
            sampleTile(title: NSLocalizedString("caregiver.inventory.summary.ended", comment: "Ended"), value: "0", tint: CaregiverUI.orange, systemImage: "calendar.badge.clock")
        }
    }

    private func sampleTile(title: String, value: String, tint: Color, systemImage: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: systemImage)
                .font(.headline.weight(.bold))
                .foregroundStyle(tint)
            Text(value)
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(tint)
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.readableSecondaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(tint.opacity(0.16), lineWidth: 1)
        )
    }

    private func sampleFilterChips(items: [(String, String, Color, Bool)]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.0) { item in
                    Label(item.0, systemImage: item.1)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(item.3 ? .white : item.2)
                        .padding(.horizontal, 12)
                        .frame(height: 38)
                        .background(item.3 ? item.2 : CaregiverUI.cardBackground, in: Capsule())
                        .overlay {
                            Capsule().stroke(item.2.opacity(0.22), lineWidth: 1)
                        }
                }
            }
            .padding(.horizontal, 2)
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.headline)
            .foregroundStyle(Color.readableSecondaryText)
            .padding(.top, 4)
    }

    private func sampleMedicationListRow(name: String, badge: String, detail: String, dose: String, inventory: String?, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                samplePillIcon(color: color)
                    .frame(width: 62, height: 62)
                VStack(alignment: .leading, spacing: 7) {
                    Text(name)
                        .font(.title2.weight(.bold))
                    Text(badge)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(color)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(color.opacity(0.13), in: Capsule())
                    Text(detail)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                    Text(dose)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                }
                Spacer(minLength: 0)
            }
            if let inventory {
                Text(inventory)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(CaregiverUI.teal)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(CaregiverUI.teal.opacity(0.12), in: Capsule())
            }
        }
        .padding(16)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(CaregiverUI.cardStroke, lineWidth: 1)
        )
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
    }

    private func sampleInventoryListRow(name: String, quantity: String, unit: String, days: String, help: String, color: Color, attention: Bool) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 14) {
                sampleBoxIcon(color: color)
                    .frame(width: 62, height: 62)
                VStack(alignment: .leading, spacing: 6) {
                    Text(name)
                        .font(.title2.weight(.bold))
                    if attention {
                        Text(NSLocalizedString("caregiver.inventory.status.low", comment: "Low badge"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(CaregiverUI.orange)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(CaregiverUI.orange.opacity(0.15), in: Capsule())
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(quantity)
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                        .foregroundStyle(color)
                    Text(unit)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.readableSecondaryText)
                }
            }
            Text(days)
                .font(.body.weight(.bold))
                .foregroundStyle(color)
            Text(help)
                .font(.subheadline)
                .foregroundStyle(Color.readableSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                sampleStepperButton(systemImage: "minus", tint: color)
                Text(quantity)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(color)
                    .frame(maxWidth: .infinity)
                sampleStepperButton(systemImage: "plus", tint: color)
            }
            .padding(6)
            .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .padding(18)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(attention ? CaregiverUI.orange.opacity(0.75) : CaregiverUI.cardStroke, lineWidth: attention ? 1.5 : 1)
        )
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
    }

    private func samplePillIcon(color: Color) -> some View {
        MedicationSymbolView(tint: color)
    }

    private func sampleBoxIcon(color: Color) -> some View {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
            .fill(color.opacity(0.12))
            .overlay {
                Image(systemName: "shippingbox.fill")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(color)
            }
    }

    private func sampleStepperButton(systemImage: String, tint: Color) -> some View {
        Image(systemName: systemImage)
            .font(.headline.weight(.bold))
            .foregroundStyle(tint)
            .frame(width: 42, height: 42)
            .background(CaregiverUI.elevatedBackground, in: Circle())
            .overlay {
                Circle().stroke(tint.opacity(0.22), lineWidth: 1)
            }
    }
}

private struct CaregiverBottomTabBar: View {
    @Binding var selectedTab: CaregiverTab
    var hasLowStock: Bool = false
    var highlightedTab: CaregiverTab?

    var body: some View {
        HStack(spacing: 12) {
            tabButton(
                title: NSLocalizedString("caregiver.tabs.today", comment: "Today tab"),
                systemImage: "house.fill",
                isSelected: selectedTab == .today,
                isHighlighted: highlightedTab == .today
            ) {
                selectedTab = .today
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab"),
                systemImage: "pills.fill",
                isSelected: selectedTab == .medications,
                isHighlighted: highlightedTab == .medications
            ) {
                selectedTab = .medications
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.inventory", comment: "Inventory tab"),
                systemImage: "shippingbox.fill",
                isSelected: selectedTab == .inventory,
                isHighlighted: highlightedTab == .inventory,
                showBadge: hasLowStock
            ) {
                selectedTab = .inventory
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.history", comment: "History tab"),
                systemImage: "clock",
                isSelected: selectedTab == .history,
                isHighlighted: highlightedTab == .history
            ) {
                selectedTab = .history
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.patients", comment: "Patients tab"),
                systemImage: "gearshape.fill",
                isSelected: selectedTab == .patients,
                isHighlighted: highlightedTab == .patients
            ) {
                selectedTab = .patients
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 8)
        .padding(.bottom, 6)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(CaregiverUI.cardStroke, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.08), radius: 14, y: 4)
    }

    private func tabButton(
        title: String,
        systemImage: String,
        isSelected: Bool,
        isHighlighted: Bool = false,
        showBadge: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 24, weight: .semibold))
                    .overlay(alignment: .topTrailing) {
                        if showBadge {
                            Circle()
                                .fill(.red)
                                .frame(width: 9, height: 9)
                                .offset(x: 4, y: -3)
                        }
                    }
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.70)
            }
            .foregroundStyle(isSelected ? Color(red: 0.0, green: 0.55, blue: 0.50) : Color.readableSecondaryText)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
            .background(
                isHighlighted ? CaregiverUI.orange.opacity(0.10) : Color.clear,
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .overlay {
                if isHighlighted {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(CaregiverUI.orange, lineWidth: 3)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

enum CaregiverUI {
    static let teal = AppTheme.primaryTeal
    static let tealDark = AppTheme.primaryTealText
    static let blue = AppTheme.caregiverBlue
    static let orange = AppTheme.orange
    static let red = AppTheme.caregiverRed
    static let background = AppTheme.screenBackground
    static let cardBackground = AppTheme.cardBackground
    static let elevatedBackground = AppTheme.elevatedBackground
    static let cardStroke = AppTheme.cardStroke
    static let cardShadow = AppTheme.caregiverCardShadow
}

struct CaregiverScreenBackground<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ZStack {
            CaregiverUI.background
                .ignoresSafeArea()
            content
        }
    }
}

struct CaregiverPatientHeader: View {
    let title: String
    let patientName: String?
    let systemImage: String
    var subtitle: String? = nil
    var subtitleLineLimit: Int = 1
    var trailing: AnyView?

    var body: some View {
        HStack(spacing: 14) {
            CaregiverAvatar(name: patientName, systemImage: systemImage)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(subtitle ?? patientNameText)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(Color.readableSecondaryText)
                    .lineLimit(subtitleLineLimit)
                    .minimumScaleFactor(0.78)
            }
            Spacer(minLength: 0)
            if let trailing {
                trailing
            }
        }
    }

    private var patientNameText: String {
        guard let patientName, !patientName.isEmpty else {
            return NSLocalizedString("caregiver.common.patient.none", comment: "No patient selected")
        }
        return String(format: NSLocalizedString("caregiver.common.patient.format", comment: "Patient name format"), patientName)
    }
}

struct CaregiverAvatar: View {
    let name: String?
    var systemImage: String = "person.crop.circle.fill"

    var body: some View {
        ZStack {
            Circle()
                .fill(CaregiverUI.cardBackground)
                .frame(width: 62, height: 62)
                .shadow(color: CaregiverUI.cardShadow, radius: 8, y: 3)
            if let initial = name?.prefix(1), !initial.isEmpty {
                Text(String(initial))
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(width: 50, height: 50)
                    .background(CaregiverUI.teal, in: Circle())
            } else {
                Image(systemName: systemImage)
                    .font(.system(size: 42))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(CaregiverUI.teal)
            }
        }
    }
}

struct CaregiverCard<Content: View>: View {
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
            .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke((accent ?? CaregiverUI.cardStroke).opacity(accent == nil ? 1 : 0.55), lineWidth: accent == nil ? 1 : 1.5)
            }
            .shadow(color: CaregiverUI.cardShadow, radius: 12, y: 5)
    }
}

struct CaregiverStatusPill: View {
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

struct CaregiverPrimaryButton: View {
    let title: String
    let systemImage: String
    var color: Color = CaregiverUI.teal
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(color, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

@MainActor
final class CaregiverMedicationViewModel: ObservableObject {
    @Published var patients: [PatientDTO] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func loadPatients() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                patients = try await apiClient.listPatients()
                let selectedPatient = patients.first { $0.id == sessionStore.currentPatientId }
                if selectedPatient == nil, sessionStore.currentPatientId != nil {
                    sessionStore.clearCurrentPatientId()
                }
                if sessionStore.currentPatientId == nil, patients.count == 1, let onlyPatient = patients.first {
                    sessionStore.setCurrentPatientId(onlyPatient.id)
                }
            } catch {
                errorMessage = NSLocalizedString("caregiver.dataUnavailable.message", comment: "Caregiver data unavailable message")
            }
        }
    }
}

struct CaregiverMedicationView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    private let onCreatePatient: () -> Void
    @StateObject private var viewModel: CaregiverMedicationViewModel

    init(
        sessionStore: SessionStore,
        onOpenPatients: @escaping () -> Void,
        onCreatePatient: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.onOpenPatients = onOpenPatients
        self.onCreatePatient = onCreatePatient
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverMedicationViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else if let errorMessage = viewModel.errorMessage {
                    CaregiverDataUnavailableView(
                        message: errorMessage,
                        onRetry: { viewModel.loadPatients() },
                        onReturnToLogin: { sessionStore.returnToCaregiverLogin() }
                    )
                } else if viewModel.patients.isEmpty {
                    CaregiverNoPatientEmptyStateView(onCreatePatient: onCreatePatient)
                } else if sessionStore.currentPatientId == nil {
                    CaregiverPatientSelectionRequiredView(
                        systemImage: "pills",
                        onOpenPatients: onOpenPatients
                    )
                } else {
                    MedicationListView(
                        sessionStore: sessionStore,
                        onOpenPatients: onOpenPatients,
                        patientName: viewModel.patients.first { $0.id == sessionStore.currentPatientId }?.displayName
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: viewModel.isLoading ? .center : .top)
            .background(AppTheme.screenBackground.ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear {
            viewModel.loadPatients()
        }
        .accessibilityIdentifier("CaregiverMedicationView")
    }
}

// MARK: - Today Tab View (standalone tab for today's schedule)

struct CaregiverTodayTabView: View {
    private let sessionStore: SessionStore
    private let patientName: String?
    private let onOpenPatients: () -> Void
    private let onOpenMedications: () -> Void
    private let onCreatePatient: () -> Void
    @StateObject private var viewModel: CaregiverMedicationViewModel

    init(
        sessionStore: SessionStore,
        patientName: String?,
        onOpenPatients: @escaping () -> Void,
        onOpenMedications: @escaping () -> Void,
        onCreatePatient: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.patientName = patientName
        self.onOpenPatients = onOpenPatients
        self.onOpenMedications = onOpenMedications
        self.onCreatePatient = onCreatePatient
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverMedicationViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else if let errorMessage = viewModel.errorMessage {
                    CaregiverDataUnavailableView(
                        message: errorMessage,
                        onRetry: { viewModel.loadPatients() },
                        onReturnToLogin: { sessionStore.returnToCaregiverLogin() }
                    )
                } else if viewModel.patients.isEmpty {
                    CaregiverNoPatientEmptyStateView(onCreatePatient: onCreatePatient)
                } else if sessionStore.currentPatientId == nil {
                    CaregiverPatientSelectionRequiredView(
                        systemImage: "calendar.badge.questionmark",
                        onOpenPatients: onOpenPatients
                    )
                } else {
                    CaregiverTodayView(
                        sessionStore: sessionStore,
                        onOpenPatients: onOpenPatients,
                        onOpenMedications: onOpenMedications,
                        patientName: patientName
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: viewModel.isLoading ? .center : .top)
            .background(CaregiverUI.background.ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear {
            viewModel.loadPatients()
        }
        .accessibilityIdentifier("CaregiverTodayTabView")
    }
}
