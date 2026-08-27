import Foundation
import UIKit
import FirebaseMessaging

/// Manages FCM device token registration and API synchronization.
///
/// When the app receives an FCM registration token (via FirebaseMessaging),
/// this manager stores it locally and provides methods to register/unregister
/// with the backend push notification endpoints.
///
/// Retains backward compatibility with APNs token forwarding (needed internally by FCM SDK).
@MainActor
final class DeviceTokenManager: ObservableObject {
    @Published private(set) var currentToken: String?
    @Published private(set) var currentFCMToken: String?

    private let userDefaults: UserDefaults
    private static let apnsTokenKey = "apns.deviceToken"
    private static let fcmTokenKey = "fcm.deviceToken"
    private static let registeredKey = "apns.registered"
    private static let fcmRegisteredKey = "fcm.registered"
    private let deleteFCMToken: @MainActor () async throws -> Void
    private let setFCMAutoInitEnabled: @MainActor (Bool) -> Void
    private let clearFCMAPNSToken: @MainActor () -> Void
    private let registerRemoteNotifications: @MainActor () -> Void
    private let unregisterRemoteNotifications: @MainActor () -> Void

    init(
        userDefaults: UserDefaults = .standard,
        deleteFCMToken: @MainActor @escaping () async throws -> Void = {
            try await withCheckedThrowingContinuation { continuation in
                Messaging.messaging().deleteToken { error in
                    if let error {
                        continuation.resume(throwing: error)
                    } else {
                        continuation.resume()
                    }
                }
            }
        },
        setFCMAutoInitEnabled: @MainActor @escaping (Bool) -> Void = {
            Messaging.messaging().isAutoInitEnabled = $0
        },
        clearFCMAPNSToken: @MainActor @escaping () -> Void = {
            Messaging.messaging().apnsToken = nil
        },
        registerRemoteNotifications: @MainActor @escaping () -> Void = {
            UIApplication.shared.registerForRemoteNotifications()
        },
        unregisterRemoteNotifications: @MainActor @escaping () -> Void = {
            UIApplication.shared.unregisterForRemoteNotifications()
        }
    ) {
        self.userDefaults = userDefaults
        self.deleteFCMToken = deleteFCMToken
        self.setFCMAutoInitEnabled = setFCMAutoInitEnabled
        self.clearFCMAPNSToken = clearFCMAPNSToken
        self.registerRemoteNotifications = registerRemoteNotifications
        self.unregisterRemoteNotifications = unregisterRemoteNotifications
        self.currentToken = userDefaults.string(forKey: Self.apnsTokenKey)
        self.currentFCMToken = userDefaults.string(forKey: Self.fcmTokenKey)
    }

    // MARK: - APNs Registration (legacy, retained for backward compat)

    /// Request APNs remote notification registration.
    func requestRemoteNotificationRegistration() {
        setFCMAutoInitEnabled(true)
        registerRemoteNotifications()
    }

    /// Called when the system provides a new APNs device token.
    func handleDeviceToken(_ deviceToken: Data) {
        let tokenString = deviceToken.map { String(format: "%02x", $0) }.joined()
        let previousToken = currentToken
        currentToken = tokenString
        userDefaults.set(tokenString, forKey: Self.apnsTokenKey)

        if tokenString != previousToken {
            // Token changed; mark as needing re-registration
            userDefaults.set(false, forKey: Self.registeredKey)
        }
    }

    /// Called when APNs registration fails.
    func handleRegistrationError(_ error: Error) {
        print("DeviceTokenManager: APNs registration failed: \(error.localizedDescription)")
    }

    // MARK: - FCM Token Handling

    /// Called when FirebaseMessaging provides or refreshes the FCM registration token.
    func handleFCMToken(_ token: String) {
        let previousToken = currentFCMToken
        currentFCMToken = token
        userDefaults.set(token, forKey: Self.fcmTokenKey)

        if token != previousToken {
            userDefaults.set(false, forKey: Self.fcmRegisteredKey)
        }
    }

    // MARK: - Backend Sync (Legacy APNs)

    /// Register the current APNs device token with the backend (legacy path).
    func registerWithBackend(apiClient: APIClient) async {
        guard let token = currentToken else {
            print("DeviceTokenManager: no APNs device token available")
            return
        }
        guard !isRegistered else {
            print("DeviceTokenManager: already registered (APNs)")
            return
        }

        do {
            try await apiClient.registerDeviceToken(token: token)
            userDefaults.set(true, forKey: Self.registeredKey)
            print("DeviceTokenManager: registered with backend (APNs)")
        } catch {
            print("DeviceTokenManager: backend registration failed (APNs): \(error.localizedDescription)")
        }
    }

    /// Unregister the current APNs device token from the backend (legacy path).
    func unregisterFromBackend(apiClient: APIClient) async {
        guard let token = currentToken else { return }

        do {
            try await apiClient.unregisterDeviceToken(token: token)
            userDefaults.set(false, forKey: Self.registeredKey)
            print("DeviceTokenManager: unregistered from backend (APNs)")
        } catch {
            print("DeviceTokenManager: backend unregister failed (APNs): \(error.localizedDescription)")
        }
    }

    // MARK: - Backend Sync (FCM Push)

    /// Determine the push environment string based on build configuration.
    static var pushEnvironment: String {
        #if DEBUG
        return "DEV"
        #else
        return "PROD"
        #endif
    }

    /// Register the current FCM token with the push backend.
    func registerFCMWithBackend(apiClient: APIClient) async throws {
        guard let token = currentFCMToken else {
            throw DeviceTokenError.noFCMToken
        }

        try await apiClient.registerPushDevice(
            token: token,
            platform: "ios",
            environment: Self.pushEnvironment
        )
        userDefaults.set(true, forKey: Self.fcmRegisteredKey)
        print("DeviceTokenManager: registered with backend (FCM)")
    }

    /// Unregister the current FCM token from the push backend.
    func unregisterFCMFromBackend(apiClient: APIClient) async throws {
        guard let token = currentFCMToken else {
            throw DeviceTokenError.noFCMToken
        }

        try await apiClient.unregisterPushDevice(token: token)
        userDefaults.set(false, forKey: Self.fcmRegisteredKey)
        print("DeviceTokenManager: unregistered from backend (FCM)")
    }

    func unregisterAllFromBackend(apiClient: APIClient) async {
        await unregisterFromBackend(apiClient: apiClient)

        do {
            try await unregisterFCMFromBackend(apiClient: apiClient)
        } catch DeviceTokenError.noFCMToken {
            userDefaults.set(false, forKey: Self.fcmRegisteredKey)
        } catch {
            print("DeviceTokenManager: backend unregister failed (FCM): \(error.localizedDescription)")
        }
    }

    /// Revokes the device-side identity even if backend unregistration failed.
    /// FCM notification payloads can otherwise be rendered by iOS without the app
    /// getting an opportunity to reject them based on its signed-out state.
    func revokeLocalPushIdentity() async {
        unregisterRemoteNotifications()
        setFCMAutoInitEnabled(false)

        do {
            try await deleteFCMToken()
        } catch {
            print("DeviceTokenManager: local FCM token deletion failed: \(error.localizedDescription)")
        }

        clearFCMAPNSToken()
        currentToken = nil
        currentFCMToken = nil
        userDefaults.removeObject(forKey: Self.apnsTokenKey)
        userDefaults.removeObject(forKey: Self.fcmTokenKey)
        userDefaults.set(false, forKey: Self.registeredKey)
        userDefaults.set(false, forKey: Self.fcmRegisteredKey)
        userDefaults.set(false, forKey: CaregiverPushSettingsViewModel.persistKey)
    }

    /// Mark as needing re-registration (e.g. when switching caregiver accounts).
    func markNeedsRegistration() {
        userDefaults.set(false, forKey: Self.registeredKey)
        userDefaults.set(false, forKey: Self.fcmRegisteredKey)
    }

    // MARK: - Private

    private var isRegistered: Bool {
        userDefaults.bool(forKey: Self.registeredKey)
    }

    private var isFCMRegistered: Bool {
        userDefaults.bool(forKey: Self.fcmRegisteredKey)
    }
}

enum DeviceTokenError: LocalizedError {
    case noFCMToken

    var errorDescription: String? {
        switch self {
        case .noFCMToken:
            return "No FCM registration token available. Ensure Firebase is configured and push notifications are enabled."
        }
    }
}
