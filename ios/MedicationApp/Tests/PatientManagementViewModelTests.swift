import XCTest
@testable import MedicationApp

final class PatientManagementViewModelTests: XCTestCase {
    private let suiteName = "PatientManagementViewModelTests"
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

    func testToggleSelectionSetsAndClears() async {
        let sessionStore = SessionStore(userDefaults: userDefaults)
        let apiClient = APIClient(baseURL: URL(string: "http://localhost:3000")!, sessionStore: sessionStore)
        let viewModel = await MainActor.run {
            PatientManagementViewModel(apiClient: apiClient, sessionStore: sessionStore)
        }

        await MainActor.run {
            XCTAssertNil(viewModel.selectedPatientId)
        }

        await MainActor.run {
            viewModel.toggleSelection(patientId: "patient-1")
            XCTAssertEqual(viewModel.selectedPatientId, "patient-1")
        }

        await MainActor.run {
            viewModel.toggleSelection(patientId: "patient-1")
            XCTAssertNil(viewModel.selectedPatientId)
        }
    }
}
