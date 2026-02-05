import XCTest

final class NotificationDeepLinkTests: XCTestCase {
    func testRoutesToTodayTabAndScrollTarget() throws {
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-05:morning:1")
        XCTAssertEqual(target?.dateKey, "2026-02-05")
        XCTAssertEqual(target?.slot, .morning)
    }

    func testTriggersSlotHighlightForTappedReminder() throws {
        let router = NotificationDeepLinkRouter()
        router.route(identifier: "notif:2026-02-05:evening:2")
        XCTAssertEqual(router.target, NotificationDeepLinkTarget(dateKey: "2026-02-05", slot: .evening))
    }
}
