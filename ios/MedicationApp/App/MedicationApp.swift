import SwiftUI

@main
struct MedicationApp: App {
    @StateObject private var sessionStore = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(sessionStore)
        }
    }
}
