import SwiftUI

// ---------------------------------------------------------------------------
// CaregiverSettingsView (012-push-foundation T028)
//
// Settings tab for caregiver mode. Contains a push notification toggle
// with "更新中" overlay while registration/unregistration is in progress.
// ---------------------------------------------------------------------------

struct CaregiverSettingsView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @StateObject private var viewModel: CaregiverPushSettingsViewModel

    init(sessionStore: SessionStore) {
        let store = sessionStore
        _viewModel = StateObject(wrappedValue: CaregiverPushSettingsViewModel(
            apiClientFactory: {
                APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: store)
            }
        ))
    }

    var body: some View {
        ZStack {
            Form {
                Section {
                    Toggle(
                        NSLocalizedString("caregiver.settings.push.toggle", comment: "Push toggle"),
                        isOn: Binding(
                            get: { viewModel.isPushEnabled },
                            set: { newValue in
                                Task {
                                    await viewModel.togglePush(enabled: newValue)
                                }
                            }
                        )
                    )
                    .accessibilityIdentifier("PushNotificationToggle")
                    .disabled(viewModel.isUpdating)
                } header: {
                    Text(NSLocalizedString("caregiver.settings.push.section.title", comment: "Push section title"))
                } footer: {
                    if viewModel.isPushEnabled {
                        Text(NSLocalizedString("caregiver.settings.push.enabled", comment: "Push enabled"))
                            .foregroundStyle(.green)
                    } else {
                        Text(NSLocalizedString("caregiver.settings.push.disabled", comment: "Push disabled"))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .accessibilityIdentifier("CaregiverSettingsView")

            if viewModel.isUpdating {
                SchedulingRefreshOverlay()
            }
        }
        .alert(
            NSLocalizedString("caregiver.settings.push.error", comment: "Error alert title"),
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            ),
            actions: {
                Button(NSLocalizedString("common.ok", comment: "OK"), role: .cancel) {
                    viewModel.errorMessage = nil
                }
            },
            message: {
                if let message = viewModel.errorMessage {
                    Text(message)
                }
            }
        )
    }
}
