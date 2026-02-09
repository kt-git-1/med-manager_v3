import SwiftUI
import UIKit
import UserNotifications

@main
struct MedicationApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var sessionStore: SessionStore
    @StateObject private var notificationRouter: NotificationDeepLinkRouter
    @StateObject private var reminderBannerPresenter: ReminderBannerPresenter
    @StateObject private var globalBannerPresenter: GlobalBannerPresenter
    @StateObject private var caregiverSessionController: CaregiverSessionController
    private let notificationCoordinator: NotificationCoordinator
    @State private var showSplash = true

    init() {
        let sessionStore = SessionStore()
        let notificationRouter = NotificationDeepLinkRouter()
        let reminderBannerPresenter = ReminderBannerPresenter()
        let globalBannerPresenter = GlobalBannerPresenter()
        let deviceTokenManager = DeviceTokenManager()
        let caregiverSessionController = CaregiverSessionController(
            sessionStore: sessionStore,
            deviceTokenManager: deviceTokenManager
        )
        _sessionStore = StateObject(wrappedValue: sessionStore)
        _notificationRouter = StateObject(wrappedValue: notificationRouter)
        _reminderBannerPresenter = StateObject(wrappedValue: reminderBannerPresenter)
        _globalBannerPresenter = StateObject(wrappedValue: globalBannerPresenter)
        _caregiverSessionController = StateObject(wrappedValue: caregiverSessionController)
        notificationCoordinator = NotificationCoordinator(
            router: notificationRouter,
            bannerPresenter: reminderBannerPresenter
        )
        UNUserNotificationCenter.current().delegate = notificationCoordinator

        let appearance = UITabBarAppearance()
        appearance.configureWithTransparentBackground()

        let itemAppearance = UITabBarItemAppearance()
        itemAppearance.normal.titleTextAttributes = [.font: UIFont.systemFont(ofSize: 10, weight: .semibold)]
        itemAppearance.selected.titleTextAttributes = [.font: UIFont.systemFont(ofSize: 10, weight: .semibold)]
        appearance.stackedLayoutAppearance = itemAppearance
        appearance.inlineLayoutAppearance = itemAppearance
        appearance.compactInlineLayoutAppearance = itemAppearance

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some Scene {
        WindowGroup {
            FullScreenContainer {
                if showSplash {
                    SplashView {
                        withAnimation(.easeOut(duration: 0.3)) {
                            showSplash = false
                        }
                    }
                } else {
                    RootView()
                        .dynamicTypeSize(.xLarge)
                }
            }
            .environmentObject(sessionStore)
            .environmentObject(notificationRouter)
            .environmentObject(reminderBannerPresenter)
            .environmentObject(globalBannerPresenter)
            .environmentObject(caregiverSessionController)
            .onAppear {
                // Wire AppDelegate → DeviceTokenManager for APNs callbacks
                appDelegate.deviceTokenManager = caregiverSessionController.tokenManager
            }
        }
    }
}
