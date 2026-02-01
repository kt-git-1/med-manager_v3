import Foundation

enum AppMode: String {
    case caregiver
    case patient
}

final class SessionStore: ObservableObject {
    @Published var mode: AppMode?
    @Published var caregiverToken: String?
    @Published var patientToken: String?

    private let baseURL: URL
    private lazy var apiClient = APIClient(baseURL: baseURL, sessionStore: self)
    private var patientRefreshTask: Task<Void, Never>?
    private var isRefreshingPatientToken = false

    init() {
        self.baseURL = URL(string: ProcessInfo.processInfo.environment["API_BASE_URL"] ?? "http://localhost:3000")!
    }

    func setMode(_ mode: AppMode) {
        self.mode = mode
    }

    func saveCaregiverToken(_ token: String) {
        caregiverToken = token
    }

    func savePatientToken(_ token: String) {
        patientToken = token
        startPatientTokenRefreshLoop()
    }

    func clearCaregiverToken() {
        caregiverToken = nil
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
}
