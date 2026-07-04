import Foundation
import UIKit
import UserNotifications
import FirebaseMessaging

// ---------------------------------------------------------------------------
// CaregiverPushSettingsViewModel (012-push-foundation T029)
//
// Manages the push notification toggle state for caregivers.
// - Toggle ON: request notification permission → get FCM token → register
// - Toggle OFF: unregister FCM token
// - Persists `isPushEnabled` to UserDefaults
// - Shows "更新中" overlay via isUpdating
// - Caregiver-only: guards against patient mode
// ---------------------------------------------------------------------------

/// Protocol for notification authorization to enable testing.
protocol NotificationAuthorizationProvider: Sendable {
    func requestAuthorization(options: UNAuthorizationOptions) async throws -> Bool
}

extension UNUserNotificationCenter: NotificationAuthorizationProvider {}

/// Protocol for FCM token retrieval to enable testing.
protocol FCMTokenProvider: Sendable {
    func token() async throws -> String
}

/// Sendable wrapper around Messaging for FCM token retrieval.
struct MessagingTokenProvider: FCMTokenProvider {
    func token() async throws -> String {
        try await Messaging.messaging().token()
    }
}

@MainActor
final class CaregiverPushSettingsViewModel: ObservableObject {
    @Published var isPushEnabled: Bool
    @Published var isUpdating: Bool = false
    @Published var errorMessage: String?

    static let persistKey = "push.isEnabled"

    private let userDefaults: UserDefaults
    private let notificationCenter: NotificationAuthorizationProvider
    private let tokenProvider: FCMTokenProvider
    private let apiClientFactory: @MainActor @Sendable () -> APIClient

    init(
        userDefaults: UserDefaults = .standard,
        notificationCenter: NotificationAuthorizationProvider = UNUserNotificationCenter.current(),
        tokenProvider: FCMTokenProvider = MessagingTokenProvider(),
        apiClientFactory: @MainActor @escaping @Sendable () -> APIClient
    ) {
        self.userDefaults = userDefaults
        self.notificationCenter = notificationCenter
        self.tokenProvider = tokenProvider
        self.apiClientFactory = apiClientFactory
        self.isPushEnabled = userDefaults.bool(forKey: Self.persistKey)
    }

    // MARK: - Toggle

    func togglePush(enabled: Bool) async {
        errorMessage = nil
        isUpdating = true

        if enabled {
            await enablePush()
        } else {
            await disablePush()
        }

        isUpdating = false
    }

    // MARK: - Enable Flow

    private func enablePush() async {
        do {
            // 1. Request notification permission
            let granted = try await notificationCenter.requestAuthorization(options: [.alert, .sound])
            guard granted else {
                errorMessage = NSLocalizedString(
                    "caregiver.settings.push.permission.denied",
                    comment: "Push permission denied"
                )
                return
            }

            // 2. Register with APNs before requesting an FCM token. Firebase may
            // reject token retrieval until the APNs token has been delivered.
            UIApplication.shared.registerForRemoteNotifications()

            // 3. Get FCM token
            let fcmToken = try await retrieveFCMTokenWithRetry()

            // 4. Register with backend
            let apiClient = apiClientFactory()
            try await apiClient.registerPushDevice(
                token: fcmToken,
                platform: "ios",
                environment: DeviceTokenManager.pushEnvironment
            )

            // 5. Success
            isPushEnabled = true
            userDefaults.set(true, forKey: Self.persistKey)
        } catch {
            print("CaregiverPushSettingsViewModel: enable push failed: \(error.localizedDescription)")
            errorMessage = NSLocalizedString(
                "caregiver.settings.push.error",
                comment: "Push registration error"
            )
        }
    }

    // MARK: - Disable Flow

    private func disablePush() async {
        do {
            // Get the FCM token to unregister
            let fcmToken = try await tokenProvider.token()

            let apiClient = apiClientFactory()
            try await apiClient.unregisterPushDevice(token: fcmToken)

            isPushEnabled = false
            userDefaults.set(false, forKey: Self.persistKey)
        } catch {
            errorMessage = NSLocalizedString(
                "caregiver.settings.push.error",
                comment: "Push unregistration error"
            )
        }
    }

    private func retrieveFCMTokenWithRetry() async throws -> String {
        var lastError: Error?
        for attempt in 0..<4 {
            do {
                return try await tokenProvider.token()
            } catch {
                lastError = error
                if attempt < 3 {
                    try await Task.sleep(nanoseconds: 750_000_000)
                }
            }
        }
        throw lastError ?? DeviceTokenError.noFCMToken
    }
}
