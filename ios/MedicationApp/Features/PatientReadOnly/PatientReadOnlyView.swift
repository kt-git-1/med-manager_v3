import SwiftUI

struct PatientReadOnlyView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var selectedTab: PatientTab = .today

    var body: some View {
        NavigationStack {
            ZStack {
                switch selectedTab {
                case .today:
                    PatientTodayView(sessionStore: sessionStore)
                case .history:
                    HistoryMonthView(sessionStore: sessionStore)
                case .settings:
                    PatientSettingsView(onLogout: { sessionStore.clearPatientToken() })
                }
            }
            .navigationTitle(navigationTitle)
            .navigationBarTitleDisplayMode(navigationTitleDisplayMode)
        }
        .safeAreaInset(edge: .bottom) {
            PatientBottomTabBar(selectedTab: $selectedTab)
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

    private var navigationTitleDisplayMode: NavigationBarItem.TitleDisplayMode {
        switch selectedTab {
        case .today:
            return .large
        case .history, .settings:
            return .inline
        }
    }
}

struct PatientSettingsView: View {
    let onLogout: () -> Void

    var body: some View {
        VStack {
            Spacer(minLength: 0)
            Button(NSLocalizedString("common.logout", comment: "Logout")) {
                onLogout()
            }
            .buttonStyle(.borderedProminent)
            .font(.headline)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("PatientSettingsView")
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
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(
            Capsule()
                .strokeBorder(Color(.separator).opacity(0.4))
        )
        .shadow(color: Color.black.opacity(0.12), radius: 18, y: 10)
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
                    .font(.system(size: 18, weight: .semibold))
                Text(title)
                    .font(.headline)
            }
            .foregroundColor(isSelected ? .accentColor : .secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(isSelected ? Color(.secondarySystemBackground) : Color.clear)
            )
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
                    .foregroundColor(.secondary)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("PatientHistoryPlaceholderView")
    }
}
