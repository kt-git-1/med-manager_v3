import XCTest
@testable import MedicationApp

@MainActor
final class DeviceTokenManagerLogoutTests: XCTestCase {
    func testRevocationClearsLocalIdentityEvenWhenFCMDeletionFails() async {
        let suiteName = "DeviceTokenManagerLogoutTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        var autoInitValues: [Bool] = []
        var clearedAPNSToken = false
        var unregisteredFromAPNs = false
        let manager = DeviceTokenManager(
            userDefaults: defaults,
            deleteFCMToken: { throw TestError.deletionFailed },
            setFCMAutoInitEnabled: { autoInitValues.append($0) },
            clearFCMAPNSToken: { clearedAPNSToken = true },
            registerRemoteNotifications: {},
            unregisterRemoteNotifications: { unregisteredFromAPNs = true }
        )
        manager.handleDeviceToken(Data([0x01, 0x02]))
        manager.handleFCMToken("old-account-fcm-token")
        defaults.set(true, forKey: CaregiverPushSettingsViewModel.persistKey)

        await manager.revokeLocalPushIdentity()

        XCTAssertNil(manager.currentToken)
        XCTAssertNil(manager.currentFCMToken)
        XCTAssertNil(defaults.string(forKey: "apns.deviceToken"))
        XCTAssertNil(defaults.string(forKey: "fcm.deviceToken"))
        XCTAssertFalse(defaults.bool(forKey: "apns.registered"))
        XCTAssertFalse(defaults.bool(forKey: "fcm.registered"))
        XCTAssertFalse(defaults.bool(forKey: CaregiverPushSettingsViewModel.persistKey))
        XCTAssertEqual(autoInitValues, [false])
        XCTAssertTrue(clearedAPNSToken)
        XCTAssertTrue(unregisteredFromAPNs)
    }

    private enum TestError: Error {
        case deletionFailed
    }
}
