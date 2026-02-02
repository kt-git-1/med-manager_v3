import Foundation

enum AppMode: String {
    case caregiver
    case patient
}

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

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        self.baseURL = SessionStore.resolveBaseURL()
        self.currentPatientId = userDefaults.string(forKey: SessionStore.currentPatientIdStorageKey)
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
    }

    func saveCaregiverToken(_ token: String) {
        if token.starts(with: "caregiver-") {
            caregiverToken = token
        } else {
            caregiverToken = "caregiver-\(token)"
        }
    }

    func savePatientToken(_ token: String) {
        patientToken = token
        startPatientTokenRefreshLoop()
    }

    func clearCaregiverToken() {
        caregiverToken = nil
        clearCurrentPatientId()
    }

    func clearPatientToken() {
        patientToken = nil
        patientRefreshTask?.cancel()
        patientRefreshTask = nil
        isRefreshingPatientToken = false
    }

    func handleAuthFailure(for mode: AppMode?) {
        switch mode {
        case .caregiver:
            clearCaregiverToken()
        case .patient:
            clearPatientToken()
        case .none:
            break
        }
    }

    private func startPatientTokenRefreshLoop() {
        patientRefreshTask?.cancel()
        patientRefreshTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 10 * 60 * 1_000_000_000)
                await refreshPatientTokenIfNeeded()
            }
        }
    }

    @MainActor
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
