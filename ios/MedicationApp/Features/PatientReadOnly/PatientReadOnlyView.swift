import SwiftUI

struct PatientReadOnlyView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var notificationRouter: NotificationDeepLinkRouter
    @EnvironmentObject private var reminderBannerPresenter: ReminderBannerPresenter
    @State private var selectedTab: PatientTab = .today
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @State private var deepLinkTarget: NotificationDeepLinkTarget?

    var body: some View {
        FullScreenContainer(content: {
            NavigationStack {
                ZStack {
                    Color(.systemGroupedBackground)
                        .ignoresSafeArea()

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
                        onLogout: { sessionStore.clearPatientToken() }
                    )
                    }
                }
                .navigationTitle("")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    NavigationHeaderView(
                        icon: navigationIconName,
                        title: navigationTitle
                    )
                }
            }
            .id(selectedTab)
            .safeAreaInset(edge: .bottom) {
                PatientBottomTabBar(selectedTab: $selectedTab)
            }
            .overlay(alignment: .top) {
                if let banner = reminderBannerPresenter.banner {
                    ReminderBannerView(banner: banner)
                }
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
        .accessibilityIdentifier("PatientReadOnlyView")
    }

    private var navigationTitle: String {
        switch selectedTab {
        case .today:
            return NSLocalizedString("patient.readonly.today.title", comment: "Today title")
        case .history:
            return NSLocalizedString("patient.readonly.history.title", comment: "History title")
        case .settings:
            return NSLocalizedString("patient.readonly.settings.title", comment: "Settings title")
        }
    }

    private var navigationIconName: String {
        switch selectedTab {
        case .today:
            return "calendar.circle.fill"
        case .history:
            return "clock.circle.fill"
        case .settings:
            return "gearshape.circle.fill"
        }
    }
}

struct PatientSettingsView: View {
    @EnvironmentObject private var globalBannerPresenter: GlobalBannerPresenter
    private let sessionStore: SessionStore
    private let apiClient: APIClient
    @ObservedObject private var schedulingCoordinator: SchedulingRefreshCoordinator
    @StateObject private var permissionManager = NotificationPermissionManager()
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @State private var showingLogoutConfirm = false
    let onLogout: () -> Void

    init(
        sessionStore: SessionStore,
        schedulingCoordinator: SchedulingRefreshCoordinator,
        onLogout: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.apiClient = APIClient(
            baseURL: SessionStore.resolveBaseURL(),
            sessionStore: sessionStore
        )
        self.schedulingCoordinator = schedulingCoordinator
        self.onLogout = onLogout
    }

    var body: some View {
        let notificationsDisabled = permissionManager.status == .denied
        Form {
            Section {
                Toggle(
                    NSLocalizedString("patient.settings.notifications.master", comment: "Enable notifications"),
                    isOn: $preferencesStore.masterEnabled
                )
                    .onChange(of: preferencesStore.masterEnabled) { _, enabled in
                        Task { await handleMasterToggle(enabled) }
                    }

                Toggle(
                    NSLocalizedString("patient.settings.notifications.rereminder", comment: "Rereminder"),
                    isOn: $preferencesStore.rereminderEnabled
                )
                    .disabled(!preferencesStore.masterEnabled)
                    .onChange(of: preferencesStore.rereminderEnabled) { _, _ in
                        Task { await rescheduleIfNeeded() }
                    }
            }
            .disabled(notificationsDisabled)

            Section(header: Text(NSLocalizedString("patient.settings.notifications.slots.title", comment: "Slots title"))) {
                toggleRow(title: NSLocalizedString("patient.settings.notifications.slot.morning", comment: "Morning"), slot: .morning)
                toggleRow(title: NSLocalizedString("patient.settings.notifications.slot.noon", comment: "Noon"), slot: .noon)
                toggleRow(title: NSLocalizedString("patient.settings.notifications.slot.evening", comment: "Evening"), slot: .evening)
                toggleRow(title: NSLocalizedString("patient.settings.notifications.slot.bedtime", comment: "Bedtime"), slot: .bedtime)
            }
            .disabled(!preferencesStore.masterEnabled || notificationsDisabled)

            if notificationsDisabled {
                Section {
                    Text(
                        NSLocalizedString(
                            "patient.settings.notifications.permission.denied",
                            comment: "Permission denied guidance"
                        )
                    )
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Button {
                    showingLogoutConfirm = true
                } label: {
                    Text(NSLocalizedString("common.logout", comment: "Logout"))
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.red, in: RoundedRectangle(cornerRadius: 14))
                }
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
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
            }
        }
        .onAppear {
            Task { await permissionManager.refreshStatus() }
        }
        .accessibilityIdentifier("PatientSettingsView")
    }

    private func toggleRow(title: String, slot: NotificationSlot) -> some View {
        Toggle(title, isOn: Binding(
            get: { preferencesStore.isSlotEnabled(slot) },
            set: { newValue in
                preferencesStore.setSlotEnabled(slot, enabled: newValue)
                Task { await rescheduleIfNeeded() }
            }
        ))
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
            slotTimes: preferencesStore.slotTimesMap(),
            caregiverPatientId: nil,
            trigger: .settingsChange
        )
    }
}

enum PatientTab: Hashable {
    case today
    case history
    case settings
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
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .glassEffect(.regular, in: .capsule)
        .padding(.bottom, 6)
    }

    private func tabButton(
        title: String,
        systemImage: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: systemImage)
                    .font(.system(size: 22, weight: .semibold))
                Text(title)
                    .font(.subheadline.weight(.bold))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .contentShape(Capsule())
            .background(isSelected ? AnyShapeStyle(Color.accentColor.opacity(0.12)) : AnyShapeStyle(Color.clear), in: Capsule())
        }
        .buttonStyle(.plain)
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
