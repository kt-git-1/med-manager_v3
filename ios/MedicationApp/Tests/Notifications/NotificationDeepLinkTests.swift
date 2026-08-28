import XCTest
@testable import MedicationApp

@MainActor
final class NotificationDeepLinkTests: XCTestCase {
    func testRoutesToTodayTabAndScrollTarget() throws {
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-05:morning:1")
        XCTAssertEqual(target?.dateKey, "2026-02-05")
        XCTAssertEqual(target?.slot, .morning)
    }

    func testTriggersSlotHighlightForTappedReminder() throws {
        let analytics = NotificationAnalyticsTrackingSpy()
        let router = NotificationDeepLinkRouter(analytics: analytics)
        router.route(identifier: "notif:2026-02-05:evening:2")
        XCTAssertEqual(router.target, NotificationDeepLinkTarget(dateKey: "2026-02-05", slot: .evening))
        XCTAssertEqual(analytics.openedSources, [.localReminder])
    }
}

@MainActor
private final class NotificationAnalyticsTrackingSpy: AnalyticsTracking {
    private(set) var openedSources: [AnalyticsNotificationSource] = []

    func logCoreActionStarted(_ action: AnalyticsCoreAction, mode: AnalyticsAppMode?) {}
    func logCoreActionCompleted(_ action: AnalyticsCoreAction, mode: AnalyticsAppMode?) {}
    func logCoreActionFailed(
        _ action: AnalyticsCoreAction,
        reason: AnalyticsFailureReason,
        mode: AnalyticsAppMode?
    ) {}

    func logNotificationOpened(source: AnalyticsNotificationSource) {
        openedSources.append(source)
    }
}
