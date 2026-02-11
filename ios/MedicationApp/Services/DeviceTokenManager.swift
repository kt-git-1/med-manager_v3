import Foundation
import UIKit

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

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        self.currentToken = userDefaults.string(forKey: Self.apnsTokenKey)
        self.currentFCMToken = userDefaults.string(forKey: Self.fcmTokenKey)
    }

    // MARK: - APNs Registration (legacy, retained for backward compat)

    /// Request APNs remote notification registration.
    func requestRemoteNotificationRegistration() {
        UIApplication.shared.registerForRemoteNotifications()
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
