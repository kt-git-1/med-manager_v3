import XCTest
import UserNotifications
@testable import MedicationApp

final class CaregiverPushSettingsTests: XCTestCase {
    private let suiteName = "CaregiverPushSettingsTests"
    private var userDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        userDefaults = UserDefaults(suiteName: suiteName)
        userDefaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        userDefaults = nil
        super.tearDown()
    }

    @MainActor
    func testInitialStateIsPushDisabledAndNotUpdating() {
        let vm = makeViewModel()

        XCTAssertFalse(vm.isPushEnabled)
        XCTAssertFalse(vm.isUpdating)
        XCTAssertNil(vm.errorMessage)
    }

    @MainActor
    func testToggleOnRequestsPermissionRetriesTokenThenRegisters() async {
        let notificationCenter = MockNotificationAuthorizationProvider(granted: true)
        let tokenProvider = MockFCMTokenProvider(results: [
            .failure(MockPushError.transientTokenFailure),
            .success("fcm-token-after-retry")
        ])
        let apiClient = MockPushDeviceAPIClient()
        let vm = makeViewModel(
            notificationCenter: notificationCenter,
            tokenProvider: tokenProvider,
            apiClient: apiClient
        )

        await vm.togglePush(enabled: true)

        XCTAssertTrue(vm.isPushEnabled)
        XCTAssertFalse(vm.isUpdating)
        XCTAssertNil(vm.errorMessage)
        XCTAssertEqual(notificationCenter.requestCount, 1)
        XCTAssertEqual(tokenProvider.requestCount, 2)
        XCTAssertEqual(userDefaults.bool(forKey: CaregiverPushSettingsViewModel.persistKey), true)
        XCTAssertEqual(apiClient.registerCalls, [
            MockPushDeviceAPIClient.RegisterCall(
                token: "fcm-token-after-retry",
                platform: "ios",
                environment: DeviceTokenManager.pushEnvironment
            )
        ])
        XCTAssertTrue(apiClient.unregisterTokens.isEmpty)
    }

    @MainActor
    func testToggleOffUnregistersDeviceAndClearsPersistedState() async {
        userDefaults.set(true, forKey: CaregiverPushSettingsViewModel.persistKey)
        let tokenProvider = MockFCMTokenProvider(results: [.success("fcm-token-1")])
        let apiClient = MockPushDeviceAPIClient()
        let vm = makeViewModel(tokenProvider: tokenProvider, apiClient: apiClient)

        XCTAssertTrue(vm.isPushEnabled)

        await vm.togglePush(enabled: false)

        XCTAssertFalse(vm.isPushEnabled)
        XCTAssertFalse(vm.isUpdating)
        XCTAssertNil(vm.errorMessage)
        XCTAssertEqual(tokenProvider.requestCount, 1)
        XCTAssertEqual(apiClient.unregisterTokens, ["fcm-token-1"])
        XCTAssertFalse(userDefaults.bool(forKey: CaregiverPushSettingsViewModel.persistKey))
    }

    @MainActor
    func testPermissionDeniedKeepsPushDisabledAndDoesNotRequestToken() async {
        let notificationCenter = MockNotificationAuthorizationProvider(granted: false)
        let tokenProvider = MockFCMTokenProvider(results: [.success("unused-token")])
        let apiClient = MockPushDeviceAPIClient()
        let vm = makeViewModel(
            notificationCenter: notificationCenter,
            tokenProvider: tokenProvider,
            apiClient: apiClient
        )

        await vm.togglePush(enabled: true)

        XCTAssertFalse(vm.isPushEnabled)
        XCTAssertFalse(vm.isUpdating)
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertEqual(notificationCenter.requestCount, 1)
        XCTAssertEqual(tokenProvider.requestCount, 0)
        XCTAssertTrue(apiClient.registerCalls.isEmpty)
        XCTAssertFalse(userDefaults.bool(forKey: CaregiverPushSettingsViewModel.persistKey))
    }

    @MainActor
    func testRegisterErrorKeepsPushDisabledAndDoesNotPersistOnState() async {
        let tokenProvider = MockFCMTokenProvider(results: [.success("fcm-token-1")])
        let apiClient = MockPushDeviceAPIClient(registerError: MockPushError.apiFailure)
        let vm = makeViewModel(tokenProvider: tokenProvider, apiClient: apiClient)

        await vm.togglePush(enabled: true)

        XCTAssertFalse(vm.isPushEnabled)
        XCTAssertFalse(vm.isUpdating)
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertEqual(apiClient.registerCalls.count, 1)
        XCTAssertFalse(userDefaults.bool(forKey: CaregiverPushSettingsViewModel.persistKey))
    }

    @MainActor
    func testPersistedStateRestoredOnInit() {
        userDefaults.set(true, forKey: CaregiverPushSettingsViewModel.persistKey)

        let vm = makeViewModel()

        XCTAssertTrue(vm.isPushEnabled)
        XCTAssertFalse(vm.isUpdating)
    }

    @MainActor
    private func makeViewModel(
        notificationCenter: MockNotificationAuthorizationProvider = MockNotificationAuthorizationProvider(granted: true),
        tokenProvider: MockFCMTokenProvider = MockFCMTokenProvider(results: [.success("fcm-token-1")]),
        apiClient: MockPushDeviceAPIClient = MockPushDeviceAPIClient()
    ) -> CaregiverPushSettingsViewModel {
        CaregiverPushSettingsViewModel(
            userDefaults: userDefaults,
            notificationCenter: notificationCenter,
            tokenProvider: tokenProvider,
            apiClientFactory: { apiClient },
            retryDelayNanoseconds: 0
        )
    }
}

private final class MockNotificationAuthorizationProvider: NotificationAuthorizationProvider, @unchecked Sendable {
    private let granted: Bool
    private(set) var requestCount = 0

    init(granted: Bool) {
        self.granted = granted
    }

    func requestAuthorization(options: UNAuthorizationOptions) async throws -> Bool {
        requestCount += 1
        return granted
    }
}

private final class MockFCMTokenProvider: FCMTokenProvider, @unchecked Sendable {
    private var results: [Result<String, Error>]
    private(set) var requestCount = 0

    init(results: [Result<String, Error>]) {
        self.results = results
    }

    func token() async throws -> String {
        requestCount += 1
        let result = results.isEmpty ? .failure(MockPushError.noMoreTokenResults) : results.removeFirst()
        switch result {
        case .success(let token):
            return token
        case .failure(let error):
            throw error
        }
    }
}

private final class MockPushDeviceAPIClient: PushDeviceAPIClient, @unchecked Sendable {
    struct RegisterCall: Equatable {
        let token: String
        let platform: String
        let environment: String
    }

    private let registerError: Error?
    private(set) var registerCalls: [RegisterCall] = []
    private(set) var unregisterTokens: [String] = []

    init(registerError: Error? = nil) {
        self.registerError = registerError
    }

    func registerPushDevice(token: String, platform: String, environment: String) async throws {
        registerCalls.append(RegisterCall(token: token, platform: platform, environment: environment))
        if let registerError {
            throw registerError
        }
    }

    func unregisterPushDevice(token: String) async throws {
        unregisterTokens.append(token)
    }
}

private enum MockPushError: Error {
    case transientTokenFailure
    case apiFailure
    case noMoreTokenResults
}
