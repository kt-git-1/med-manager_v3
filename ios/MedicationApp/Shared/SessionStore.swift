import Foundation

enum AppMode: String {
    case caregiver
    case patient
}

@MainActor
final class SessionStore: ObservableObject {
    @Published var mode: AppMode?
    @Published var caregiverToken: String?
    @Published var patientToken: String?
    @Published var currentPatientId: String? {
        didSet {
            persistCurrentPatientId()
        }
    }
    @Published var shouldRedirectCaregiverToMedicationTab = false
    @Published var shouldNavigateToCaregiverLogin = false

    private let baseURL: URL
    private let userDefaults: UserDefaults
    private let secureStorage: SessionSecureStorage
    private let authService: AuthService
    private let now: () -> Date
    private lazy var apiClient = APIClient(baseURL: baseURL, sessionStore: self)
    private var caregiverRefreshTask: Task<Void, Never>?
    private var isRefreshingCaregiverToken = false
    private static let sessionDuration: TimeInterval = 30 * 24 * 60 * 60
    private static let caregiverRefreshBuffer: TimeInterval = 2 * 60
    static let currentPatientIdStorageKey = "currentPatientId"
    static let caregiverTokenStorageKey = "caregiverToken"
    static let caregiverRefreshTokenStorageKey = "caregiverRefreshToken"
    static let caregiverExpiresAtStorageKey = "caregiverSessionExpiresAt"
    static let patientTokenStorageKey = "patientToken"
    static let patientExpiresAtStorageKey = "patientSessionExpiresAt"
    static let lastModeStorageKey = "lastAppMode"
    static let modeTutorialSeenStorageKeyPrefix = "modeTutorialSeen."
    static let patientTutorialPreviewStorageKey = "debug.patientTutorialPreview"

    init(
        userDefaults: UserDefaults = .standard,
        secureStorage: SessionSecureStorage = SessionKeychainStore(),
        authService: AuthService = AuthService(),
        now: @escaping () -> Date = Date.init
    ) {
        self.userDefaults = userDefaults
        self.secureStorage = secureStorage
        self.authService = authService
        self.now = now
        self.baseURL = SessionStore.resolveBaseURL()
        self.currentPatientId = userDefaults.string(forKey: SessionStore.currentPatientIdStorageKey)
        migrateLegacyTokensIfNeeded()
        self.caregiverToken = restoredCaregiverToken(
            tokenKey: SessionStore.caregiverTokenStorageKey,
            expiresAtKey: SessionStore.caregiverExpiresAtStorageKey,
            extraKeysToRemoveWhenExpired: [SessionStore.caregiverRefreshTokenStorageKey]
        )
        self.patientToken = restoredPatientToken(tokenKey: SessionStore.patientTokenStorageKey)

        if let rawMode = userDefaults.string(forKey: SessionStore.lastModeStorageKey),
           let storedMode = AppMode(rawValue: rawMode) {
            self.mode = storedMode
        }

        applyUITestSessionOverridesIfNeeded()
        applyTutorialPreviewOverridesIfNeeded()

        secureStorage.removeString(forKey: SessionStore.patientExpiresAtStorageKey)
    }

    static func resolveBaseURL() -> URL {
        let envValue = ProcessInfo.processInfo.environment["API_BASE_URL"]
        if let envValue, let url = URL(string: envValue) {
            return url
        }
        let infoValue = Bundle.main.infoDictionary?["API_BASE_URL"] as? String
        if let infoValue, let url = URL(string: infoValue) {
            return url
        }
        print("SessionStore: API_BASE_URL missing or invalid, defaulting to \(AppConstants.defaultAPIBaseURL)")
        return AppConstants.defaultAPIBaseURL
    }

    func setMode(_ mode: AppMode) {
        self.mode = mode
        userDefaults.set(mode.rawValue, forKey: SessionStore.lastModeStorageKey)
    }

    func shouldShowModeTutorial(for mode: AppMode) -> Bool {
        !userDefaults.bool(forKey: Self.modeTutorialSeenStorageKey(for: mode))
    }

    func shouldForceModeTutorial(for mode: AppMode) -> Bool {
        let arguments = ProcessInfo.processInfo.arguments
        let isLaunchArgumentForced = arguments.contains("-ForceModeTutorial")
            || arguments.contains("-ForceModeTutorial.\(mode.rawValue)")
        #if DEBUG
        if mode == .patient, userDefaults.bool(forKey: Self.patientTutorialPreviewStorageKey) {
            return true
        }
        #endif
        return isLaunchArgumentForced
    }

    private func applyTutorialPreviewOverridesIfNeeded() {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-CaregiverTutorialPreview") {
            saveCaregiverToken("tutorial-preview-caregiver-token")
            return
        }
        #endif

        guard isPatientTutorialPreviewActive else { return }
        mode = .patient
        if patientToken == nil {
            patientToken = "tutorial-preview-patient-token"
        }
    }

    private func applyUITestSessionOverridesIfNeeded() {
        #if DEBUG
        let env = ProcessInfo.processInfo.environment
        guard env["UITEST_SESSION_BOOTSTRAP"] == "1" else { return }

        clearSessionForUITestBootstrap()

        if let caregiverToken = env["UITEST_CAREGIVER_TOKEN"], !caregiverToken.isEmpty {
            saveCaregiverToken(caregiverToken)
        }
        if let patientToken = env["UITEST_PATIENT_TOKEN"], !patientToken.isEmpty {
            savePatientToken(patientToken)
        }
        if let currentPatientId = env["UITEST_CURRENT_PATIENT_ID"], !currentPatientId.isEmpty {
            setCurrentPatientId(currentPatientId)
        }

        if env["UITEST_MARK_TUTORIALS_SEEN"] == "1" {
            markModeTutorialSeen(for: .caregiver)
            markModeTutorialSeen(for: .patient)
        }
        if let rawMode = env["UITEST_MODE"], let mode = AppMode(rawValue: rawMode) {
            setMode(mode)
        }
        #endif
    }

    #if DEBUG
    private func clearSessionForUITestBootstrap() {
        caregiverToken = nil
        patientToken = nil
        mode = nil
        shouldRedirectCaregiverToMedicationTab = false
        shouldNavigateToCaregiverLogin = false
        userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
        userDefaults.removeObject(forKey: CaregiverPushSettingsViewModel.persistKey)
        removeCaregiverSession()
        removePatientSession()
        clearCurrentPatientId()
        caregiverRefreshTask?.cancel()
        caregiverRefreshTask = nil
        isRefreshingCaregiverToken = false
    }
    #endif

    var isPatientTutorialPreviewActive: Bool {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        return arguments.contains("-ForceModeTutorial")
            || arguments.contains("-ForceModeTutorial.patient")
            || userDefaults.bool(forKey: Self.patientTutorialPreviewStorageKey)
        #else
        return false
        #endif
    }

    func markModeTutorialSeen(for mode: AppMode) {
        userDefaults.set(true, forKey: Self.modeTutorialSeenStorageKey(for: mode))
    }

    func resetMode() {
        mode = nil
        userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
    }

    func resetAfterAccountDeletion() {
        caregiverToken = nil
        patientToken = nil
        mode = nil
        shouldRedirectCaregiverToMedicationTab = false
        shouldNavigateToCaregiverLogin = false
        userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
        userDefaults.removeObject(forKey: CaregiverPushSettingsViewModel.persistKey)
        removeCaregiverSession()
        removePatientSession()
        clearCurrentPatientId()
        caregiverRefreshTask?.cancel()
        caregiverRefreshTask = nil
        isRefreshingCaregiverToken = false
    }

    func saveCaregiverToken(_ token: String) {
        saveCaregiverSession(
            SupabaseSession(
                accessToken: token,
                refreshToken: nil,
                expiresIn: nil
            )
        )
    }

    func saveCaregiverSession(_ session: SupabaseSession, preserveCurrentPatientId: Bool = false) {
        guard let accessToken = session.accessToken, !accessToken.isEmpty else { return }
        setMode(.caregiver)
        if !preserveCurrentPatientId {
            clearCurrentPatientId()
        }
        caregiverToken = normalizedCaregiverToken(accessToken)
        persistToken(caregiverToken, key: SessionStore.caregiverTokenStorageKey)
        if let refreshToken = session.refreshToken, !refreshToken.isEmpty {
            persistToken(refreshToken, key: SessionStore.caregiverRefreshTokenStorageKey)
        }
        persistExpiry(forKey: SessionStore.caregiverExpiresAtStorageKey, duration: caregiverAccessTokenDuration(for: session))
        NotificationCenter.default.post(name: .caregiverDidLogin, object: nil)
    }

    func savePatientToken(_ token: String) {
        patientToken = token
        persistToken(token, key: SessionStore.patientTokenStorageKey)
        secureStorage.removeString(forKey: SessionStore.patientExpiresAtStorageKey)
    }

    func clearCaregiverToken() {
        caregiverToken = nil
        removeCaregiverSession()
        caregiverRefreshTask?.cancel()
        caregiverRefreshTask = nil
        isRefreshingCaregiverToken = false
        userDefaults.removeObject(forKey: CaregiverPushSettingsViewModel.persistKey)
        if mode == .caregiver {
            mode = nil
            userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
        }
        clearCurrentPatientId()
    }

    func returnToCaregiverLogin() {
        clearCaregiverToken()
        setMode(.caregiver)
    }

    @discardableResult
    func handleIncomingURL(_ url: URL) -> Bool {
        guard Self.isCaregiverLoginURL(url) else { return false }
        clearCaregiverToken()
        setMode(.caregiver)
        shouldNavigateToCaregiverLogin = true
        return true
    }

    func clearPatientToken() {
        patientToken = nil
        removePatientSession()
        if mode == .patient {
            mode = nil
            userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
        }
    }

    func handleAuthFailure(for mode: AppMode?) {
        switch mode {
        case .caregiver:
            invalidateCaregiverToken()
            NotificationCenter.default.post(name: .authFailure, object: nil)
        case .patient:
            invalidatePatientToken()
            NotificationCenter.default.post(name: .authFailure, object: nil)
        case .none:
            break
        }
    }

    func invalidateCaregiverToken() {
        caregiverToken = nil
        removeCaregiverSession()
        caregiverRefreshTask?.cancel()
        caregiverRefreshTask = nil
        isRefreshingCaregiverToken = false
        clearCurrentPatientId()
    }

    func invalidatePatientToken() {
        patientToken = nil
        removePatientSession()
    }

    func refreshCaregiverTokenIfNeeded() async {
        if let caregiverRefreshTask {
            await caregiverRefreshTask.value
            return
        }
        guard mode == .caregiver, caregiverToken != nil else { return }
        let shouldRefresh = isExpiringSoon(
            expiresAtKey: SessionStore.caregiverExpiresAtStorageKey,
            buffer: Self.caregiverRefreshBuffer
        )
        guard shouldRefresh else { return }
        guard let refreshToken = secureStorage.string(forKey: SessionStore.caregiverRefreshTokenStorageKey),
              !refreshToken.isEmpty else {
            if isExpired(expiresAtKey: SessionStore.caregiverExpiresAtStorageKey) {
                invalidateCaregiverToken()
            }
            return
        }
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            self.isRefreshingCaregiverToken = true
            defer { self.isRefreshingCaregiverToken = false }
            do {
                let refreshed = try await self.authService.refreshSession(refreshToken: refreshToken)
                self.saveCaregiverSession(refreshed, preserveCurrentPatientId: true)
            } catch {
                self.invalidateCaregiverToken()
                NotificationCenter.default.post(name: .authFailure, object: nil)
            }
        }
        caregiverRefreshTask = task
        await task.value
        caregiverRefreshTask = nil
    }

    func setCurrentPatientId(_ patientId: String?) {
        currentPatientId = patientId
    }

    func clearCurrentPatientId() {
        currentPatientId = nil
    }

    func clearCurrentPatientIfMatches(_ patientId: String) {
        guard currentPatientId == patientId else { return }
        currentPatientId = nil
    }

    func handlePatientRevoked(_ patientId: String) {
        guard currentPatientId == patientId else { return }
        currentPatientId = nil
        shouldRedirectCaregiverToMedicationTab = true
    }

    private func persistCurrentPatientId() {
        if let currentPatientId {
            userDefaults.set(currentPatientId, forKey: SessionStore.currentPatientIdStorageKey)
        } else {
            userDefaults.removeObject(forKey: SessionStore.currentPatientIdStorageKey)
        }
    }

    private static func modeTutorialSeenStorageKey(for mode: AppMode) -> String {
        "\(modeTutorialSeenStorageKeyPrefix)\(mode.rawValue)"
    }

    private static func isCaregiverLoginURL(_ url: URL) -> Bool {
        let scheme = url.scheme?.lowercased()
        let host = url.host?.lowercased()
        let path = url.path.lowercased()

        if scheme == "okusurimimamori", host == "auth", path == "/login" {
            return true
        }

        if scheme == "https",
           ["okusuri-mimamori.com", "www.okusuri-mimamori.com"].contains(host),
           ["/auth/confirmed", "/auth/login"].contains(path) {
            return true
        }

        return false
    }

    private func restoredToken(
        tokenKey: String,
        expiresAtKey: String,
        extraKeysToRemoveWhenExpired: [String] = []
    ) -> String? {
        guard !isExpired(expiresAtKey: expiresAtKey) else {
            secureStorage.removeString(forKey: tokenKey)
            secureStorage.removeString(forKey: expiresAtKey)
            extraKeysToRemoveWhenExpired.forEach { secureStorage.removeString(forKey: $0) }
            return nil
        }
        return secureStorage.string(forKey: tokenKey)
    }

    private func restoredCaregiverToken(
        tokenKey: String,
        expiresAtKey: String,
        extraKeysToRemoveWhenExpired: [String] = []
    ) -> String? {
        if isExpired(expiresAtKey: expiresAtKey),
           secureStorage.string(forKey: SessionStore.caregiverRefreshTokenStorageKey)?.isEmpty != false {
            secureStorage.removeString(forKey: tokenKey)
            secureStorage.removeString(forKey: expiresAtKey)
            extraKeysToRemoveWhenExpired.forEach { secureStorage.removeString(forKey: $0) }
            return nil
        }
        guard let token = secureStorage.string(forKey: tokenKey) else { return nil }
        let normalized = normalizedCaregiverToken(token)
        if normalized != token {
            secureStorage.setString(normalized, forKey: tokenKey)
        }
        return normalized
    }

    private func restoredPatientToken(tokenKey: String) -> String? {
        secureStorage.removeString(forKey: SessionStore.patientExpiresAtStorageKey)
        return secureStorage.string(forKey: tokenKey)
    }

    private func normalizedCaregiverToken(_ token: String) -> String {
        if token.starts(with: AppConstants.caregiverTokenPrefix) {
            return token
        }
        return "\(AppConstants.caregiverTokenPrefix)\(token)"
    }

    private func persistToken(_ token: String?, key: String) {
        if let token, !token.isEmpty {
            secureStorage.setString(token, forKey: key)
        } else {
            secureStorage.removeString(forKey: key)
        }
    }

    private func persistExpiry(forKey key: String, duration: TimeInterval = SessionStore.sessionDuration) {
        let expiresAt = now().addingTimeInterval(duration).timeIntervalSince1970
        secureStorage.setString(String(expiresAt), forKey: key)
    }

    private func caregiverAccessTokenDuration(for session: SupabaseSession) -> TimeInterval {
        guard let expiresIn = session.expiresIn, expiresIn > 0 else {
            return Self.sessionDuration
        }
        return TimeInterval(expiresIn)
    }

    private func isExpired(expiresAtKey: String) -> Bool {
        guard let raw = secureStorage.string(forKey: expiresAtKey),
              let expiresAt = TimeInterval(raw) else {
            return false
        }
        return Date(timeIntervalSince1970: expiresAt) <= now()
    }

    private func isExpiringSoon(expiresAtKey: String, buffer: TimeInterval) -> Bool {
        guard let raw = secureStorage.string(forKey: expiresAtKey),
              let expiresAt = TimeInterval(raw) else {
            return false
        }
        return Date(timeIntervalSince1970: expiresAt).timeIntervalSince(now()) <= buffer
    }

    private func removeCaregiverSession() {
        secureStorage.removeString(forKey: SessionStore.caregiverTokenStorageKey)
        secureStorage.removeString(forKey: SessionStore.caregiverRefreshTokenStorageKey)
        secureStorage.removeString(forKey: SessionStore.caregiverExpiresAtStorageKey)
    }

    private func removePatientSession() {
        secureStorage.removeString(forKey: SessionStore.patientTokenStorageKey)
        secureStorage.removeString(forKey: SessionStore.patientExpiresAtStorageKey)
    }

    private func migrateLegacyTokensIfNeeded() {
        if secureStorage.string(forKey: SessionStore.caregiverTokenStorageKey) == nil,
           let legacyCaregiverToken = userDefaults.string(forKey: SessionStore.caregiverTokenStorageKey) {
            secureStorage.setString(
                normalizedCaregiverToken(legacyCaregiverToken),
                forKey: SessionStore.caregiverTokenStorageKey
            )
            persistExpiry(forKey: SessionStore.caregiverExpiresAtStorageKey)
        }
        if secureStorage.string(forKey: SessionStore.patientTokenStorageKey) == nil,
           let legacyPatientToken = userDefaults.string(forKey: SessionStore.patientTokenStorageKey) {
            secureStorage.setString(legacyPatientToken, forKey: SessionStore.patientTokenStorageKey)
            persistExpiry(forKey: SessionStore.patientExpiresAtStorageKey)
        }
        userDefaults.removeObject(forKey: SessionStore.caregiverTokenStorageKey)
        userDefaults.removeObject(forKey: SessionStore.patientTokenStorageKey)
    }
}
