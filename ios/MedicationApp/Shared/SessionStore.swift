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

    private let baseURL: URL
    private let userDefaults: UserDefaults
    private lazy var apiClient = APIClient(baseURL: baseURL, sessionStore: self)
    private var patientRefreshTask: Task<Void, Never>?
    private var isRefreshingPatientToken = false
    private static let currentPatientIdStorageKey = "currentPatientId"
    private static let caregiverTokenStorageKey = "caregiverToken"
    private static let patientTokenStorageKey = "patientToken"
    private static let lastModeStorageKey = "lastAppMode"

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        self.baseURL = SessionStore.resolveBaseURL()
        self.currentPatientId = userDefaults.string(forKey: SessionStore.currentPatientIdStorageKey)
        self.caregiverToken = userDefaults.string(forKey: SessionStore.caregiverTokenStorageKey)
        self.patientToken = userDefaults.string(forKey: SessionStore.patientTokenStorageKey)

        if let rawMode = userDefaults.string(forKey: SessionStore.lastModeStorageKey),
           let storedMode = AppMode(rawValue: rawMode) {
            self.mode = storedMode
        } else if caregiverToken != nil {
            self.mode = .caregiver
        } else if patientToken != nil {
            self.mode = .patient
        }

        if patientToken != nil {
            startPatientTokenRefreshLoop()
        }
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
        print("SessionStore: API_BASE_URL missing or invalid, defaulting to http://localhost:3000")
        return URL(string: "http://localhost:3000")!
    }

    func setMode(_ mode: AppMode) {
        self.mode = mode
        userDefaults.set(mode.rawValue, forKey: SessionStore.lastModeStorageKey)
    }

    func resetMode() {
        mode = nil
        userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
    }

    func saveCaregiverToken(_ token: String) {
        if token.starts(with: "caregiver-") {
            caregiverToken = token
        } else {
            caregiverToken = "caregiver-\(token)"
        }
        userDefaults.set(caregiverToken, forKey: SessionStore.caregiverTokenStorageKey)
    }

    func savePatientToken(_ token: String) {
        patientToken = token
        userDefaults.set(token, forKey: SessionStore.patientTokenStorageKey)
        startPatientTokenRefreshLoop()
    }

    func clearCaregiverToken() {
        caregiverToken = nil
        userDefaults.removeObject(forKey: SessionStore.caregiverTokenStorageKey)
        if mode == .caregiver {
            mode = nil
            userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
        }
        clearCurrentPatientId()
    }

    func clearPatientToken() {
        patientToken = nil
        userDefaults.removeObject(forKey: SessionStore.patientTokenStorageKey)
        if mode == .patient {
            mode = nil
            userDefaults.removeObject(forKey: SessionStore.lastModeStorageKey)
        }
        patientRefreshTask?.cancel()
        patientRefreshTask = nil
        isRefreshingPatientToken = false
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
        userDefaults.removeObject(forKey: SessionStore.caregiverTokenStorageKey)
        clearCurrentPatientId()
    }

    func invalidatePatientToken() {
        patientToken = nil
        userDefaults.removeObject(forKey: SessionStore.patientTokenStorageKey)
        patientRefreshTask?.cancel()
        patientRefreshTask = nil
        isRefreshingPatientToken = false
    }

    private func startPatientTokenRefreshLoop() {
        patientRefreshTask?.cancel()
        patientRefreshTask = Task { @MainActor [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(600))
                await refreshPatientTokenIfNeeded()
            }
        }
    }

    private func refreshPatientTokenIfNeeded() async {
        guard patientToken != nil else { return }
        guard !isRefreshingPatientToken else { return }
        isRefreshingPatientToken = true
        defer { isRefreshingPatientToken = false }
        do {
            let refreshed = try await apiClient.refreshPatientSessionToken()
            savePatientToken(refreshed)
        } catch {
            if case APIError.unauthorized = error {
                clearPatientToken()
            } else if case APIError.forbidden = error {
                clearPatientToken()
            }
        }
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
}
