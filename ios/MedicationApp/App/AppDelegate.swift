import UIKit
import UserNotifications

/// AppDelegate adapter for handling APNs device token registration.
///
/// SwiftUI's App lifecycle does not provide APNs callbacks directly,
/// so we use `@UIApplicationDelegateAdaptor` to bridge them.
final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Set by MedicationApp on launch to forward device tokens.
    weak var deviceTokenManager: DeviceTokenManager?

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task { @MainActor in
            deviceTokenManager?.handleDeviceToken(deviceToken)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        Task { @MainActor in
            deviceTokenManager?.handleRegistrationError(error)
        }
    }
}
