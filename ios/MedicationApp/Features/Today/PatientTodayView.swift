import SwiftUI

struct PatientTodayView: View {
    private let sessionStore: SessionStore
    @StateObject private var viewModel: PatientTodayViewModel
    @State private var showingConfirm = false

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: PatientTodayViewModel(apiClient: APIClient(baseURL: baseURL, sessionStore: store))
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.white
                .ignoresSafeArea()
            content

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

            if viewModel.isUpdating {
                Color.black.opacity(0.2)
                    .ignoresSafeArea()
                LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                    .padding(16)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(radius: 6)
            }
        }
        .onAppear {
            viewModel.handleAppear()
        }
        .onDisappear {
            viewModel.handleDisappear()
        }
        .alert(
            NSLocalizedString("patient.today.confirm.title", comment: "Confirm title"),
            isPresented: $showingConfirm,
            presenting: viewModel.confirmDose
        ) { _ in
            Button(NSLocalizedString("patient.today.confirm.action", comment: "Confirm action")) {
                viewModel.recordConfirmedDose()
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
        } message: { dose in
            Text(confirmMessage(for: dose))
        }
        .onChange(of: viewModel.confirmDose) { _, newValue in
            showingConfirm = newValue != nil
        }
        .sensoryFeedback(.success, trigger: viewModel.toastMessage)
        .accessibilityIdentifier("PatientTodayView")
        .environmentObject(sessionStore)
    }

    private var content: some View {
        Group {
            if viewModel.isLoading {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else if viewModel.items.isEmpty {
                EmptyStateView(
                    title: NSLocalizedString("patient.today.empty.title", comment: "Empty title"),
                    message: NSLocalizedString("patient.today.empty.message", comment: "Empty message")
                )
            } else {
                List {
                    if !plannedItems.isEmpty {
                        Section {
                            ForEach(plannedItems) { dose in
                                PatientTodayRow(
                                    dose: dose,
                                    timeText: viewModel.timeText(for: dose.scheduledAt),
                                    onRecord: { viewModel.confirmRecord(for: dose) }
                                )
                                .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("patient.today.section.planned", comment: "Today planned"))
                                .font(.headline)
                                .foregroundStyle(.primary)
                                .textCase(nil)
                        }
                    }

                    if !takenItems.isEmpty {
                        Section {
                            ForEach(takenItems) { dose in
                                PatientTodayRow(
                                    dose: dose,
                                    timeText: viewModel.timeText(for: dose.scheduledAt),
                                    onRecord: { viewModel.confirmRecord(for: dose) }
                                )
                                .listRowSeparator(.hidden)
                            }
                        } header: {
                            Text(NSLocalizedString("patient.today.section.taken", comment: "Taken"))
                                .font(.headline)
                                .foregroundStyle(.primary)
                                .textCase(nil)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(Color.white)
            }
        }
        .safeAreaPadding(.top)
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

    private func confirmMessage(for dose: ScheduleDoseDTO) -> String {
        let timeText = viewModel.timeText(for: dose.scheduledAt)
        return String(
            format: NSLocalizedString("patient.today.confirm.message", comment: "Confirm message"),
            dose.medicationSnapshot.name,
            timeText
        )
    }
}

private struct PatientTodayRow: View {
    let dose: ScheduleDoseDTO
    let timeText: String
    let onRecord: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(timeText)
                        .font(.headline)
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                    Text(dose.medicationSnapshot.name)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                    Text(dose.medicationSnapshot.dosageText)
                        .font(.body)
                        .foregroundColor(.secondary)
                }
                Spacer()
                if let statusText = statusText(for: dose.effectiveStatus) {
                    Text(statusText)
                        .font(.caption.weight(.semibold))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(statusBackground(for: dose.effectiveStatus))
                        .foregroundStyle(statusForeground(for: dose.effectiveStatus))
                        .clipShape(Capsule())
                }
            }

            if shouldShowRecordButton(for: dose.effectiveStatus) {
                Button(action: onRecord) {
                    Text(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
                        .font(.title3.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .accessibilityLabel(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(backgroundColor(for: dose.effectiveStatus))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(isMissed ? Color.red.opacity(0.35) : Color.clear, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 8, y: 3)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private var isMissed: Bool {
        dose.effectiveStatus == .missed
    }

    private var accessibilitySummary: String {
        var parts = [timeText, dose.medicationSnapshot.name, dose.medicationSnapshot.dosageText]
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

    private func statusForeground(for status: DoseStatusDTO?) -> Color {
        switch status {
        case .missed:
            return Color.red
        case .taken, .pending, .none:
            return Color.primary
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

    private func shouldShowRecordButton(for status: DoseStatusDTO?) -> Bool {
        switch status {
        case .pending, .none:
            return Date() >= recordAvailableFrom
        case .taken, .missed:
            return false
        }
    }

    private var recordAvailableFrom: Date {
        dose.scheduledAt.addingTimeInterval(-30 * 60)
    }
}
