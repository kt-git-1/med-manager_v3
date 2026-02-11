import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

/// AppDelegate adapter for handling APNs device token registration and Firebase setup.
///
/// SwiftUI's App lifecycle does not provide APNs callbacks directly,
/// so we use `@UIApplicationDelegateAdaptor` to bridge them.
final class AppDelegate: NSObject, UIApplicationDelegate, @preconcurrency MessagingDelegate {
    /// Set by MedicationApp on launch to forward device tokens.
    weak var deviceTokenManager: DeviceTokenManager?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Configure Firebase (requires GoogleService-Info.plist in bundle)
        if let _ = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
        } else {
            print("AppDelegate: GoogleService-Info.plist not found — Firebase not configured. Push notifications will be unavailable.")
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Forward APNs token to Firebase (required for FCM on iOS)
        Messaging.messaging().apnsToken = deviceToken

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

    // MARK: - MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in
            deviceTokenManager?.handleFCMToken(fcmToken)
        }
    }
}
