import XCTest
@testable import MedicationApp

// ---------------------------------------------------------------------------
// T008: Unit tests for push deep link routing
//
// Validates remote push payload parsing produces correct navigation target.
// Tests for routeFromRemotePush will fail until Phase 3 implements the
// extension to NotificationDeepLinkRouter.
// Backward compat test for existing local notification parsing passes now.
// ---------------------------------------------------------------------------

final class PushDeepLinkTests: XCTestCase {

    // MARK: - Remote Push Payload Parsing (routeFromRemotePush)

    func testDoseTakenPushWithMorningSlot() throws {
        throw XCTSkip("routeFromRemotePush not yet implemented (Phase 3 T030).")
        // Given: remote push userInfo with type=DOSE_TAKEN, date, slot=morning
        // When: routeFromRemotePush is called
        // Then: target = NotificationDeepLinkTarget(dateKey: "2026-02-11", slot: .morning)
        //
        // let router = NotificationDeepLinkRouter()
        // let userInfo: [AnyHashable: Any] = [
        //     "type": "DOSE_TAKEN",
        //     "date": "2026-02-11",
        //     "slot": "morning"
        // ]
        // router.routeFromRemotePush(userInfo: userInfo)
        // XCTAssertEqual(
        //     router.target,
        //     NotificationDeepLinkTarget(dateKey: "2026-02-11", slot: .morning)
        // )
    }

    func testDoseTakenPushWithBedtimeSlot() throws {
        throw XCTSkip("routeFromRemotePush not yet implemented (Phase 3 T030).")
        // Given: remote push with slot=bedtime
        // When: routeFromRemotePush is called
        // Then: target.slot == .bedtime
        //
        // let router = NotificationDeepLinkRouter()
        // let userInfo: [AnyHashable: Any] = [
        //     "type": "DOSE_TAKEN",
        //     "date": "2026-02-11",
        //     "slot": "bedtime"
        // ]
        // router.routeFromRemotePush(userInfo: userInfo)
        // XCTAssertEqual(router.target?.slot, .bedtime)
    }

    func testUnknownTypeReturnsNilTarget() throws {
        throw XCTSkip("routeFromRemotePush not yet implemented (Phase 3 T030).")
        // Given: remote push with unknown type "OTHER"
        // When: routeFromRemotePush is called
        // Then: target is nil
        //
        // let router = NotificationDeepLinkRouter()
        // let userInfo: [AnyHashable: Any] = [
        //     "type": "OTHER",
        //     "date": "2026-02-11",
        //     "slot": "morning"
        // ]
        // router.routeFromRemotePush(userInfo: userInfo)
        // XCTAssertNil(router.target)
    }

    func testMissingDateKeyReturnsNilTarget() throws {
        throw XCTSkip("routeFromRemotePush not yet implemented (Phase 3 T030).")
        // Given: remote push missing "date" key
        // When: routeFromRemotePush is called
        // Then: target is nil
        //
        // let router = NotificationDeepLinkRouter()
        // let userInfo: [AnyHashable: Any] = [
        //     "type": "DOSE_TAKEN",
        //     "slot": "morning"
        // ]
        // router.routeFromRemotePush(userInfo: userInfo)
        // XCTAssertNil(router.target)
    }

    func testMissingSlotKeyReturnsNilTarget() throws {
        throw XCTSkip("routeFromRemotePush not yet implemented (Phase 3 T030).")
        // Given: remote push missing "slot" key
        // When: routeFromRemotePush is called
        // Then: target is nil
        //
        // let router = NotificationDeepLinkRouter()
        // let userInfo: [AnyHashable: Any] = [
        //     "type": "DOSE_TAKEN",
        //     "date": "2026-02-11"
        // ]
        // router.routeFromRemotePush(userInfo: userInfo)
        // XCTAssertNil(router.target)
    }

    // MARK: - Backward Compat: Existing Local Notification Parsing

    func testExistingLocalNotificationParsingStillWorks() throws {
        // This test passes now — validates existing behavior is not broken
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-11:morning:1")
        XCTAssertEqual(target?.dateKey, "2026-02-11")
        XCTAssertEqual(target?.slot, .morning)
    }

    func testExistingLocalNotificationWithEveningSlot() throws {
        // Backward compat: existing evening slot parsing
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-11:evening:2")
        XCTAssertEqual(target?.dateKey, "2026-02-11")
        XCTAssertEqual(target?.slot, .evening)
    }

    func testExistingLocalNotificationWithBedtimeSlot() throws {
        // Backward compat: existing bedtime slot parsing
        let target = NotificationDeepLinkParser.parse(identifier: "notif:2026-02-11:bedtime:1")
        XCTAssertEqual(target?.dateKey, "2026-02-11")
        XCTAssertEqual(target?.slot, .bedtime)
    }
}
