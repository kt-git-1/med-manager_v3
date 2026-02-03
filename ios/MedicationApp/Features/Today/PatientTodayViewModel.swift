import Foundation
import SwiftUI

@MainActor
final class PatientTodayViewModel: ObservableObject {
    @Published var items: [ScheduleDoseDTO] = []
    @Published var isLoading = false
    @Published var isUpdating = false
    @Published var errorMessage: String?
    @Published var toastMessage: String?
    @Published var confirmDose: ScheduleDoseDTO?

    private let apiClient: APIClient
    private let dateFormatter: DateFormatter
    private let timeFormatter: DateFormatter

    init(apiClient: APIClient) {
        self.apiClient = apiClient
        self.dateFormatter = DateFormatter()
        self.dateFormatter.locale = Locale(identifier: "ja_JP")
        self.dateFormatter.dateStyle = .medium
        self.dateFormatter.timeStyle = .none
        self.timeFormatter = DateFormatter()
        self.timeFormatter.locale = Locale(identifier: "ja_JP")
        self.timeFormatter.dateStyle = .none
        self.timeFormatter.timeStyle = .short
    }

    func load(showLoading: Bool) {
        guard !isLoading else { return }
        isLoading = showLoading
        isUpdating = true
        errorMessage = nil
        Task {
            defer {
                isLoading = false
                isUpdating = false
            }
            do {
                let doses = try await apiClient.fetchPatientToday()
                items = doses.sorted { $0.scheduledAt < $1.scheduledAt }
            } catch {
                items = []
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    func confirmRecord(for dose: ScheduleDoseDTO) {
        if dose.effectiveStatus == .taken {
            showToast(NSLocalizedString("patient.today.alreadyRecorded", comment: "Already recorded"))
            return
        }
        confirmDose = dose
    }

    func recordConfirmedDose() {
        guard let dose = confirmDose else { return }
        confirmDose = nil
        isUpdating = true
        Task {
            defer { isUpdating = false }
            do {
                _ = try await apiClient.createPatientDoseRecord(
                    input: DoseRecordCreateRequestDTO(
                        medicationId: dose.medicationId,
                        scheduledAt: dose.scheduledAt
                    )
                )
                showToast(NSLocalizedString("patient.today.recorded", comment: "Recorded"))
                load(showLoading: false)
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
            }
        }
    }

    func timeText(for date: Date) -> String {
        timeFormatter.string(from: date)
    }

    func dateText(for date: Date) -> String {
        dateFormatter.string(from: date)
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation {
                self.toastMessage = nil
            }
        }
    }
}
