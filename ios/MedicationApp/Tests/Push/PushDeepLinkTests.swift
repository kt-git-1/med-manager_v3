import XCTest
@testable import MedicationApp

@MainActor
final class PushDeepLinkTests: XCTestCase {
    func testDoseTakenPushWithMorningSlotRoutesToHistoryTarget() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026-02-11",
            "slot": "morning"
        ])

        XCTAssertEqual(
            router.target,
            NotificationDeepLinkTarget(dateKey: "2026-02-11", slot: .morning)
        )
    }

    func testDoseTakenPushWithBedtimeSlotRoutesToHistoryTarget() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026-02-11",
            "slot": "bedtime"
        ])

        XCTAssertEqual(router.target?.dateKey, "2026-02-11")
        XCTAssertEqual(router.target?.slot, .bedtime)
    }

    func testDoseMissedPushRoutesToHistoryTarget() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_MISSED",
            "date": "2026-02-11",
            "slot": "noon"
        ])

        XCTAssertEqual(
            router.target,
            NotificationDeepLinkTarget(dateKey: "2026-02-11", slot: .noon)
        )
    }

    func testUnknownRemotePushTypeDoesNotRoute() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "OTHER",
            "date": "2026-02-11",
            "slot": "morning"
        ])

        XCTAssertNil(router.target)
    }

    func testMissingDateDoesNotRoute() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "slot": "morning"
        ])

        XCTAssertNil(router.target)
    }

    func testMissingSlotDoesNotRoute() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026-02-11"
        ])

        XCTAssertNil(router.target)
    }

    func testInvalidSlotDoesNotRoute() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026-02-11",
            "slot": "midnight"
        ])

        XCTAssertNil(router.target)
    }

    func testInvalidDateFormatDoesNotRoute() {
        let router = NotificationDeepLinkRouter()

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026/02/11",
            "slot": "morning"
        ])

        XCTAssertNil(router.target)
    }

    func testInvalidRemotePushDoesNotClearExistingTarget() {
        let router = NotificationDeepLinkRouter()
        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026-02-11",
            "slot": "morning"
        ])
        XCTAssertEqual(router.target?.slot, .morning)

        router.routeFromRemotePush(userInfo: [
            "type": "DOSE_TAKEN",
            "date": "2026-02-12",
            "slot": "midnight"
        ])

        XCTAssertEqual(
            router.target,
            NotificationDeepLinkTarget(dateKey: "2026-02-11", slot: .morning)
        )
    }

    func testExistingLocalNotificationParsingStillWorks() {
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-11:morning:1")
        XCTAssertEqual(target?.dateKey, "2026-02-11")
        XCTAssertEqual(target?.slot, .morning)
    }

    func testExistingLocalNotificationWithEveningSlot() {
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-11:evening:2")
        XCTAssertEqual(target?.dateKey, "2026-02-11")
        XCTAssertEqual(target?.slot, .evening)
    }

    func testExistingLocalNotificationWithBedtimeSlot() {
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-11:bedtime:1")
        XCTAssertEqual(target?.dateKey, "2026-02-11")
        XCTAssertEqual(target?.slot, .bedtime)
    }
}
