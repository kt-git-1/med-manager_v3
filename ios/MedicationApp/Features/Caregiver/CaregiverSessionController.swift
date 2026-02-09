import SwiftUI

@MainActor
final class CaregiverSessionController: ObservableObject {
    private let sessionStore: SessionStore
    private let deviceTokenManager: DeviceTokenManager
    private var hasRegisteredPush = false

    init(
        sessionStore: SessionStore,
        deviceTokenManager: DeviceTokenManager = DeviceTokenManager()
    ) {
        self.sessionStore = sessionStore
        self.deviceTokenManager = deviceTokenManager
    }

    func updateScenePhase(_ phase: ScenePhase) {
        if phase == .active {
            registerPushTokenIfNeeded()
        }
    }

    /// Provide access to the device token manager for AppDelegate bridging.
    var tokenManager: DeviceTokenManager { deviceTokenManager }

    // MARK: - Push Notification Registration

    private func registerPushTokenIfNeeded() {
        guard sessionStore.mode == .caregiver, sessionStore.caregiverToken != nil else { return }

        // Request APNs registration (idempotent; iOS returns cached token if already registered)
        deviceTokenManager.requestRemoteNotificationRegistration()

        // Register the token with our backend
        guard !hasRegisteredPush else { return }
        hasRegisteredPush = true

        let baseURL = SessionStore.resolveBaseURL()
        let apiClient = APIClient(baseURL: baseURL, sessionStore: sessionStore)
        Task {
            await deviceTokenManager.registerWithBackend(apiClient: apiClient)
        }
    }
}
