import SwiftUI
import UIKit
import UserNotifications

@main
struct MedicationApp: App {
    @StateObject private var sessionStore: SessionStore
    @StateObject private var notificationRouter: NotificationDeepLinkRouter
    @StateObject private var reminderBannerPresenter: ReminderBannerPresenter
    @StateObject private var globalBannerPresenter: GlobalBannerPresenter
    @StateObject private var caregiverSessionController: CaregiverSessionController
    private let notificationCoordinator: NotificationCoordinator

    init() {
        let sessionStore = SessionStore()
        let notificationRouter = NotificationDeepLinkRouter()
        let reminderBannerPresenter = ReminderBannerPresenter()
        let globalBannerPresenter = GlobalBannerPresenter()
        let caregiverSessionController = CaregiverSessionController(
            sessionStore: sessionStore,
            bannerPresenter: globalBannerPresenter
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
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor.systemBackground
        appearance.shadowColor = UIColor.separator

        let itemAppearance = UITabBarItemAppearance()
        itemAppearance.normal.titleTextAttributes = [.font: UIFont.systemFont(ofSize: 15, weight: .semibold)]
        itemAppearance.selected.titleTextAttributes = [.font: UIFont.systemFont(ofSize: 15, weight: .semibold)]
        appearance.stackedLayoutAppearance = itemAppearance
        appearance.inlineLayoutAppearance = itemAppearance
        appearance.compactInlineLayoutAppearance = itemAppearance

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
        UITabBar.appearance().isTranslucent = false
        UITabBar.appearance().itemPositioning = .fill
        UITabBar.appearance().itemWidth = 90
        UITabBar.appearance().itemSpacing = 12
    }

    var body: some Scene {
        WindowGroup {
            FullScreenContainer {
                RootView()
                    .dynamicTypeSize(.xLarge)
            }
            .environmentObject(sessionStore)
            .environmentObject(notificationRouter)
            .environmentObject(reminderBannerPresenter)
            .environmentObject(globalBannerPresenter)
            .environmentObject(caregiverSessionController)
        }
    }
}
