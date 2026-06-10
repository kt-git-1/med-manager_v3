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
                }
                .navigationTitle("")
                .navigationBarTitleDisplayMode(.inline)
            }
            .id(selectedTab)
            .safeAreaInset(edge: .bottom) {
                PatientBottomTabBar(selectedTab: $selectedTab)
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
            await refreshNotificationSchedule(trigger: .appLaunch)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await refreshNotificationSchedule(trigger: .appForeground) }
            }
        }
        .accessibilityIdentifier("PatientReadOnlyView")
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

    var body: some View {
        HStack(spacing: 12) {
            tabButton(
                title: NSLocalizedString("patient.readonly.today.tab", comment: "Today tab"),
                systemImage: "calendar",
                isSelected: selectedTab == .today
            ) {
                selectedTab = .today
            }
            tabButton(
                title: NSLocalizedString("patient.readonly.history.tab", comment: "History tab"),
                systemImage: "clock",
                isSelected: selectedTab == .history
            ) {
                selectedTab = .history
            }
            tabButton(
                title: NSLocalizedString("patient.readonly.settings.tab", comment: "Settings tab"),
                systemImage: "gearshape",
                isSelected: selectedTab == .settings
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
