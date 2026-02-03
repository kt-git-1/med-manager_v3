import SwiftUI

struct CaregiverTodayView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    private let headerView: AnyView?
    @StateObject private var viewModel: CaregiverTodayViewModel

    init(
        sessionStore: SessionStore? = nil,
        onOpenPatients: @escaping () -> Void = {},
        headerView: AnyView? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.onOpenPatients = onOpenPatients
        self.headerView = headerView
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverTodayViewModel(apiClient: APIClient(baseURL: baseURL, sessionStore: store))
        )
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.white)
            .overlay(alignment: .top) {
                if let toastMessage = viewModel.toastMessage {
                    Text(toastMessage)
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial)
                        .clipShape(Capsule())
                        .shadow(radius: 4)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .accessibilityLabel(toastMessage)
                }
            }
            .overlay {
                if viewModel.isUpdating {
                    ZStack {
                        Color.black.opacity(0.2)
                            .ignoresSafeArea()
                        LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                            .padding(16)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .shadow(radius: 6)
                    }
                }
            }
            .onAppear {
            if sessionStore.currentPatientId != nil {
                viewModel.load(showLoading: true)
            }
        }
        .onChange(of: sessionStore.currentPatientId) { _, newValue in
            if newValue != nil {
                viewModel.load(showLoading: true)
            } else {
                viewModel.reset()
            }
        }
            .accessibilityIdentifier("CaregiverTodayView")
            .environmentObject(sessionStore)
    }

    private var content: some View {
        Group {
            if sessionStore.currentPatientId == nil {
                VStack(spacing: 12) {
                    EmptyStateView(
                        title: NSLocalizedString("caregiver.medications.noSelection.title", comment: "No selection title"),
                        message: NSLocalizedString("caregiver.medications.noSelection.message", comment: "No selection message")
                    )
                    Button(NSLocalizedString("caregiver.medications.noSelection.action", comment: "Go to patients action")) {
                        onOpenPatients()
                    }
                    .buttonStyle(.borderedProminent)
                    .font(.headline)
                }
                .padding(24)
                .frame(maxWidth: .infinity)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                .padding(.horizontal, 24)
            } else if viewModel.isLoading {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else if viewModel.items.isEmpty {
                EmptyStateView(
                    title: NSLocalizedString("caregiver.today.empty.title", comment: "Empty title"),
                    message: NSLocalizedString("caregiver.today.empty.message", comment: "Empty message")
                )
            } else {
                let baseList = List {
                    if let headerView {
                        headerView
                            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                    }
                    if !plannedItems.isEmpty {
                        Section {
                            ForEach(plannedItems) { dose in
                                CaregiverTodayRow(
                                    dose: dose,
                                    timeText: viewModel.timeText(for: dose.scheduledAt),
                                    actionTitle: NSLocalizedString("caregiver.today.record.button", comment: "Record"),
                                    recordedByText: nil,
                                    isDestructive: false,
                                    onAction: { viewModel.recordDose(dose) }
                                )
                                .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("caregiver.today.section.planned", comment: "Today planned"))
                                .font(.headline)
                                .foregroundStyle(.primary)
                                .textCase(nil)
                        }
                    }

                    if !takenItems.isEmpty {
                        Section {
                            ForEach(takenItems) { dose in
                                CaregiverTodayRow(
                                    dose: dose,
                                    timeText: viewModel.timeText(for: dose.scheduledAt),
                                    actionTitle: NSLocalizedString("caregiver.today.delete.button", comment: "Delete"),
                                    recordedByText: recordedByText(for: dose),
                                    isDestructive: true,
                                    onAction: { viewModel.deleteDose(dose) }
                                )
                                .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("caregiver.today.section.taken", comment: "Taken"))
                                .font(.headline)
                                .foregroundStyle(.primary)
                                .textCase(nil)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(Color.white)
                let listWithInsets = headerView == nil ? AnyView(baseList.safeAreaPadding(.top)) : AnyView(baseList)
                listWithInsets
            }
        }
    }

    private var plannedItems: [ScheduleDoseDTO] {
        viewModel.items.filter { dose in
            switch dose.effectiveStatus {
            case .taken:
                return false
            case .pending, .missed, .none:
                return true
            }
        }
    }

    private var takenItems: [ScheduleDoseDTO] {
        viewModel.items.filter { $0.effectiveStatus == .taken }
    }

    private func recordedByText(for dose: ScheduleDoseDTO) -> String? {
        guard dose.effectiveStatus == .taken, let recordedByType = dose.recordedByType else {
            return nil
        }
        let nameKey: String
        switch recordedByType {
        case .patient:
            nameKey = "caregiver.today.recordedBy.patient"
        case .caregiver:
            nameKey = "caregiver.today.recordedBy.caregiver"
        }
        let name = NSLocalizedString(nameKey, comment: "Recorded by actor")
        let format = NSLocalizedString("caregiver.today.recordedBy", comment: "Recorded by label")
        return String(format: format, name)
    }
}

private struct CaregiverTodayRow: View {
    let dose: ScheduleDoseDTO
    let timeText: String
    let actionTitle: String
    let recordedByText: String?
    let isDestructive: Bool
    let onAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(timeText)
                        .font(.headline)
                    Text(dose.medicationSnapshot.name)
                        .font(.title3.weight(.semibold))
                    Text(dose.medicationSnapshot.dosageText)
                        .font(.body)
                        .foregroundColor(.secondary)
                    if let recordedByText {
                        Text(recordedByText)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                if let statusText = statusText(for: dose.effectiveStatus) {
                    Text(statusText)
                        .font(.caption.weight(.semibold))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(statusBackground(for: dose.effectiveStatus))
                        .clipShape(Capsule())
                }
            }

            actionButton
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(backgroundColor(for: dose.effectiveStatus))
        )
        .shadow(color: Color.black.opacity(0.06), radius: 8, y: 3)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private var accessibilitySummary: String {
        var parts = [timeText, dose.medicationSnapshot.name, dose.medicationSnapshot.dosageText]
        if let recordedByText {
            parts.append(recordedByText)
        }
        if let statusText = statusText(for: dose.effectiveStatus) {
            parts.append(statusText)
        }
        return parts.joined(separator: ", ")
    }

    private func statusText(for status: DoseStatusDTO?) -> String? {
        switch status {
        case .pending:
            return NSLocalizedString("patient.today.status.pending", comment: "Pending")
        case .taken:
            return NSLocalizedString("patient.today.status.taken", comment: "Taken")
        case .missed:
            return NSLocalizedString("patient.today.status.missed", comment: "Missed")
        case .none:
            return nil
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        if isDestructive {
            Button(action: onAction) {
                Text(actionTitle)
                    .font(.title3.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .tint(.red)
            .accessibilityLabel(actionTitle)
        } else {
            Button(action: onAction) {
                Text(actionTitle)
                    .font(.title3.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(.accentColor)
            .accessibilityLabel(actionTitle)
        }
    }

    private func statusBackground(for status: DoseStatusDTO?) -> Color {
        switch status {
        case .missed:
            return Color.red.opacity(0.15)
        case .taken:
            return Color.green.opacity(0.12)
        case .pending:
            return Color(.secondarySystemBackground)
        case .none:
            return Color(.secondarySystemBackground)
        }
    }

    private func backgroundColor(for status: DoseStatusDTO?) -> Color {
        switch status {
        case .missed:
            return Color.red.opacity(0.08)
        case .taken:
            return Color.green.opacity(0.06)
        case .pending, .none:
            return Color(.systemBackground)
        }
    }
}
