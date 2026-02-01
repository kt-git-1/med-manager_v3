import Foundation

enum AppMode: String {
    case caregiver
    case patient
}

final class SessionStore: ObservableObject {
    @Published var mode: AppMode?
    @Published var caregiverToken: String?
    @Published var patientToken: String?

    func setMode(_ mode: AppMode) {
        self.mode = mode
    }

    func saveCaregiverToken(_ token: String) {
        caregiverToken = token
    }

    func savePatientToken(_ token: String) {
        patientToken = token
    }
}
