import Foundation
import UIKit

/// Manages APNs device token registration and API synchronization.
///
/// When the app receives a device token from APNs, this manager sends it
/// to the backend so push notifications can be delivered to the caregiver.
@MainActor
final class DeviceTokenManager: ObservableObject {
    @Published private(set) var currentToken: String?

    private let userDefaults: UserDefaults
    private static let tokenKey = "apns.deviceToken"
    private static let registeredKey = "apns.registered"

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        self.currentToken = userDefaults.string(forKey: Self.tokenKey)
    }

    // MARK: - APNs Registration

    /// Request APNs remote notification registration.
    func requestRemoteNotificationRegistration() {
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// Called when the system provides a new device token.
    func handleDeviceToken(_ deviceToken: Data) {
        let tokenString = deviceToken.map { String(format: "%02x", $0) }.joined()
        let previousToken = currentToken
        currentToken = tokenString
        userDefaults.set(tokenString, forKey: Self.tokenKey)

        if tokenString != previousToken {
            // Token changed; mark as needing re-registration
            userDefaults.set(false, forKey: Self.registeredKey)
        }
    }

    /// Called when APNs registration fails.
    func handleRegistrationError(_ error: Error) {
        print("DeviceTokenManager: APNs registration failed: \(error.localizedDescription)")
    }

    // MARK: - Backend Sync

    /// Register the current device token with the backend (for the current caregiver).
    func registerWithBackend(apiClient: APIClient) async {
        guard let token = currentToken else {
            print("DeviceTokenManager: no device token available")
            return
        }
        guard !isRegistered else {
            print("DeviceTokenManager: already registered")
            return
        }

        do {
            try await apiClient.registerDeviceToken(token: token)
            userDefaults.set(true, forKey: Self.registeredKey)
            print("DeviceTokenManager: registered with backend")
        } catch {
            print("DeviceTokenManager: backend registration failed: \(error.localizedDescription)")
        }
    }

    /// Unregister the current device token from the backend (e.g. on sign out).
    func unregisterFromBackend(apiClient: APIClient) async {
        guard let token = currentToken else { return }

        do {
            try await apiClient.unregisterDeviceToken(token: token)
            userDefaults.set(false, forKey: Self.registeredKey)
            print("DeviceTokenManager: unregistered from backend")
        } catch {
            print("DeviceTokenManager: backend unregister failed: \(error.localizedDescription)")
        }
    }

    /// Mark as needing re-registration (e.g. when switching caregiver accounts).
    func markNeedsRegistration() {
        userDefaults.set(false, forKey: Self.registeredKey)
    }

    // MARK: - Private

    private var isRegistered: Bool {
        userDefaults.bool(forKey: Self.registeredKey)
    }
}
