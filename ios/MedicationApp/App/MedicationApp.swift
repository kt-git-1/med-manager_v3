import SwiftUI
import UIKit

@main
struct MedicationApp: App {
    @StateObject private var sessionStore = SessionStore()

    init() {
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
        }
    }
}
