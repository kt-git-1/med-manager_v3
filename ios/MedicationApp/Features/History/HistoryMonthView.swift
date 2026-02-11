import SwiftUI

struct HistoryMonthView: View {
    private static let historyTimeZone = AppConstants.defaultTimeZone
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = historyTimeZone
        calendar.locale = AppConstants.japaneseLocale
        calendar.firstWeekday = 1
        return calendar
    }()
    private static let dateKeyFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryMonthView.calendar
        formatter.timeZone = HistoryMonthView.historyTimeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
    private static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryMonthView.calendar
        formatter.timeZone = HistoryMonthView.historyTimeZone
        formatter.dateFormat = "yyyy年M月"
        return formatter
    }()

    private let sessionStore: SessionStore
    private let entitlementStore: EntitlementStore?
    private let apiClient: APIClient
    @StateObject private var viewModel: HistoryViewModel
    @State private var displayedMonth: Date
    @State private var selectedDate: Date?
    @State private var showRetentionLock = false
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var highlightedSlot: NotificationSlot?

    init(sessionStore: SessionStore? = nil, entitlementStore: EntitlementStore? = nil, deepLinkTarget: Binding<NotificationDeepLinkTarget?> = .constant(nil)) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.entitlementStore = entitlementStore
        self._deepLinkTarget = deepLinkTarget
        let baseURL = SessionStore.resolveBaseURL()
        let client = APIClient(baseURL: baseURL, sessionStore: store)
        self.apiClient = client
        _viewModel = StateObject(
            wrappedValue: HistoryViewModel(
                apiClient: client,
                sessionStore: store
            )
        )
        _displayedMonth = State(initialValue: HistoryMonthView.startOfMonth(for: Date()))
    }

    var body: some View {
        FullScreenContainer(
            content: {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        header
                        retentionBanner

                        if viewModel.isLoadingMonth && viewModel.month == nil {
                            LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                        } else if let errorMessage = viewModel.monthErrorMessage {
                            errorSection(message: errorMessage, retry: loadMonth)
                        } else {
                            calendarGrid
                            legend
                            HistoryDayDetailView(viewModel: viewModel, selectedDate: selectedDate, highlightedSlot: highlightedSlot)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 120)
                }
                .refreshable {
                    loadMonth()
                    updateSelectionForDisplayedMonth()
                    if let selectedDate {
                        viewModel.loadDay(date: HistoryMonthView.dateKeyFormatter.string(from: selectedDate))
                    }
                }
            },
            overlay: viewModel.isUpdating ? AnyView(updatingOverlay) : nil
        )
        .onAppear {
            loadMonth()
        }
        .onReceive(NotificationCenter.default.publisher(for: .presetTimesUpdated)) { _ in
            loadMonth()
            updateSelectionForDisplayedMonth()
        }
        .onChange(of: displayedMonth) { _, _ in
            loadMonth()
            updateSelectionForDisplayedMonth()
        }
        .onChange(of: viewModel.month) { _, _ in
            updateSelectionForDisplayedMonth()
        }
        .onChange(of: selectedDate) { _, newValue in
            guard let date = newValue else { return }
            viewModel.loadDay(date: HistoryMonthView.dateKeyFormatter.string(from: date))
        }
        .onChange(of: viewModel.retentionLocked) { _, locked in
            showRetentionLock = locked
        }
        .fullScreenCover(isPresented: $showRetentionLock) {
            if let cutoffDate = viewModel.retentionCutoffDate {
                HistoryRetentionLockView(
                    mode: sessionStore.mode ?? .caregiver,
                    cutoffDate: cutoffDate,
                    entitlementStore: entitlementStore ?? EntitlementStore(),
                    onDismiss: {
                        showRetentionLock = false
                        // Navigate back to the current month
                        withAnimation(.easeInOut(duration: 0.25)) {
                            displayedMonth = HistoryMonthView.startOfMonth(for: Date())
                        }
                    },
                    onRefresh: {
                        showRetentionLock = false
                        loadMonth()
                    }
                )
            }
        }
        .accessibilityIdentifier("HistoryMonthView")
        .onChange(of: deepLinkTarget) { _, newTarget in
            handleDeepLink(newTarget)
        }
        .onAppear {
            // Handle deep link that was set before view appeared (cold start)
            if let target = deepLinkTarget {
                handleDeepLink(target)
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                PDFExportButton(
                    entitlementStore: entitlementStore ?? EntitlementStore(),
                    sessionStore: sessionStore,
                    patientId: sessionStore.currentPatientId ?? "",
                    apiClient: apiClient
                )
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Button(action: showPreviousMonth) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canGoToPreviousMonth)
            .accessibilityLabel(NSLocalizedString("history.month.previous", comment: "Previous month"))

            Spacer()

            Text(HistoryMonthView.monthFormatter.string(from: displayedMonth))
                .font(.title3.weight(.semibold))

            Spacer()

            Button(action: showNextMonth) {
                Image(systemName: "chevron.right")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canGoToNextMonth)
            .accessibilityLabel(NSLocalizedString("history.month.next", comment: "Next month"))
        }
    }

    private var retentionBanner: some View {
        Group {
            if let entitlementStore, !entitlementStore.isPremium {
                Text(String(
                    format: NSLocalizedString(
                        "history.retention.banner.free",
                        comment: "Free retention banner"
                    ),
                    FeatureGate.historyCutoffDate()
                ))
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 6)
                .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
                .accessibilityIdentifier("HistoryRetentionBannerFree")
            }
        }
    }

    private var calendarGrid: some View {
        VStack(spacing: 8) {
            HStack(spacing: 0) {
                ForEach(weekdaySymbols, id: \.self) { symbol in
                    Text(symbol)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }

            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                ForEach(dayCells.indices, id: \.self) { index in
                    if let date = dayCells[index] {
                        dayCell(for: date)
                    } else {
                        Color.clear
                            .frame(height: 44)
                    }
                }
            }
        }
    }

    private func dayCell(for date: Date) -> some View {
        let dateKey = HistoryMonthView.dateKeyFormatter.string(from: date)
        let summary = summariesByDate[dateKey]
        let prnCount = prnCountByDate[dateKey]
        let dayNumber = Self.calendar.component(.day, from: date)
        let isSelected = selectedDate.map { Self.calendar.isDate($0, inSameDayAs: date) } ?? false

        return Button(action: { selectedDate = date }) {
            VStack(spacing: 6) {
                Text("\(dayNumber)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                    .frame(maxWidth: .infinity)
                HStack(spacing: 4) {
                    slotDot(summary?.morning ?? .none, slotKey: "morning")
                    slotDot(summary?.noon ?? .none, slotKey: "noon")
                    slotDot(summary?.evening ?? .none, slotKey: "evening")
                    slotDot(summary?.bedtime ?? .none, slotKey: "bedtime")
                    if let prnCount, prnCount > 0 {
                        prnDot(count: prnCount)
                    }
                }
            }
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(isSelected ? Color.accentColor : Color.primary.opacity(0.05))
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func slotDot(_ status: HistorySlotSummaryStatusDTO, slotKey: String) -> some View {
        if status != .none {
            Circle()
                .fill(statusColor(status))
                .frame(width: 6, height: 6)
                .accessibilityLabel("\(slotLabel(for: slotKey)) \(statusLabel(for: status))")
                .accessibilityIdentifier("HistorySlotDot-\(slotKey)")
        }
    }

    private func statusColor(_ status: HistorySlotSummaryStatusDTO) -> Color {
        switch status {
        case .taken:
            return Color.green
        case .missed:
            return Color.red
        case .pending:
            return Color.gray
        case .none:
            return Color.clear
        }
    }

    private func prnDot(count: Int) -> some View {
        Circle()
            .fill(Color.purple)
            .frame(width: 6, height: 6)
            .accessibilityLabel(
                String(
                    format: NSLocalizedString("history.month.prn.count", comment: "PRN count label"),
                    count
                )
            )
            .accessibilityIdentifier("HistoryPrnDot")
    }

    private var legend: some View {
        HStack(spacing: 16) {
            legendItem(color: Color.green, title: NSLocalizedString("history.legend.taken", comment: "Legend taken"))
            legendItem(color: Color.red, title: NSLocalizedString("history.legend.missed", comment: "Legend missed"))
            legendItem(color: Color.gray, title: NSLocalizedString("history.legend.pending", comment: "Legend pending"))
            legendItem(color: Color.purple, title: NSLocalizedString("history.legend.prn", comment: "Legend PRN"))
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("HistoryLegend")
    }

    private func legendItem(color: Color, title: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(title)
        }
        .accessibilityLabel(title)
    }

    private var weekdaySymbols: [String] {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.japaneseLocale
        formatter.calendar = HistoryMonthView.calendar
        return formatter.shortWeekdaySymbols
    }

    private var dayCells: [Date?] {
        guard let range = Self.calendar.range(of: .day, in: .month, for: displayedMonth) else {
            return []
        }
        let firstWeekday = Self.calendar.component(.weekday, from: displayedMonth)
        let leadingEmpty = (firstWeekday - Self.calendar.firstWeekday + 7) % 7
        var cells = Array(repeating: Optional<Date>.none, count: leadingEmpty)
        for day in range {
            if let date = Self.calendar.date(byAdding: .day, value: day - 1, to: displayedMonth) {
                cells.append(date)
            }
        }
        return cells
    }

    private var summariesByDate: [String: HistorySlotSummaryDTO] {
        guard let month = viewModel.month else { return [:] }
        return Dictionary(uniqueKeysWithValues: month.days.map { ($0.date, $0.slotSummary) })
    }

    private var prnCountByDate: [String: Int] {
        viewModel.month?.prnCountByDay ?? [:]
    }

    private var currentMonthStart: Date {
        HistoryMonthView.startOfMonth(for: Date())
    }

    private var minMonth: Date {
        // Allow navigating back up to 36 months. The backend enforces the actual
        // retention limit and the lock UI handles the restriction display.
        Self.calendar.date(byAdding: .month, value: -36, to: currentMonthStart) ?? currentMonthStart
    }

    private var canGoToPreviousMonth: Bool {
        displayedMonth > minMonth
    }

    private var canGoToNextMonth: Bool {
        displayedMonth < currentMonthStart
    }

    private func showPreviousMonth() {
        guard canGoToPreviousMonth,
              let previous = Self.calendar.date(byAdding: .month, value: -1, to: displayedMonth) else {
            return
        }
        withAnimation(.easeInOut(duration: 0.25)) {
            displayedMonth = Self.calendar.date(from: Self.calendar.dateComponents([.year, .month], from: previous)) ?? previous
        }
    }

    private func showNextMonth() {
        guard canGoToNextMonth,
              let next = Self.calendar.date(byAdding: .month, value: 1, to: displayedMonth) else {
            return
        }
        withAnimation(.easeInOut(duration: 0.25)) {
            displayedMonth = Self.calendar.date(from: Self.calendar.dateComponents([.year, .month], from: next)) ?? next
        }
    }

    private func loadMonth() {
        let components = Self.calendar.dateComponents([.year, .month], from: displayedMonth)
        guard let year = components.year, let month = components.month else { return }
        viewModel.loadMonth(year: year, month: month)
    }

    private func updateSelectionForDisplayedMonth() {
        let today = Date()
        if Self.calendar.isDate(today, equalTo: displayedMonth, toGranularity: .month) {
            selectedDate = today
            return
        }
        if selectedDate == nil,
           let firstDay = Self.calendar.date(byAdding: .day, value: 0, to: displayedMonth) {
            selectedDate = firstDay
        } else if let selectedDate,
                  !Self.calendar.isDate(selectedDate, equalTo: displayedMonth, toGranularity: .month),
                  let firstDay = Self.calendar.date(byAdding: .day, value: 0, to: displayedMonth) {
            self.selectedDate = firstDay
        }
    }

    private static func startOfMonth(for date: Date) -> Date {
        let components = calendar.dateComponents([.year, .month], from: date)
        return calendar.date(from: components) ?? date
    }

    private var updatingOverlay: some View {
        SchedulingRefreshOverlay()
    }

    private func errorSection(message: String, retry: @escaping () -> Void) -> some View {
        VStack(spacing: 12) {
            ErrorStateView(message: message)
            Button(NSLocalizedString("common.retry", comment: "Retry")) {
                retry()
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("HistoryRetryButton")
        }
    }

    private func slotLabel(for key: String) -> String {
        switch key {
        case "morning":
            return NSLocalizedString("history.slot.morning", comment: "Morning slot")
        case "noon":
            return NSLocalizedString("history.slot.noon", comment: "Noon slot")
        case "evening":
            return NSLocalizedString("history.slot.evening", comment: "Evening slot")
        case "bedtime":
            return NSLocalizedString("history.slot.bedtime", comment: "Bedtime slot")
        default:
            return key
        }
    }

    private func statusLabel(for status: HistorySlotSummaryStatusDTO) -> String {
        switch status {
        case .taken:
            return NSLocalizedString("history.status.taken", comment: "Taken")
        case .missed:
            return NSLocalizedString("history.status.missed", comment: "Missed")
        case .pending:
            return NSLocalizedString("history.status.pending", comment: "Pending")
        case .none:
            return ""
        }
    }

    private func prnCountLabel(count: Int) -> String {
        String(
            format: NSLocalizedString("history.month.prn.count", comment: "PRN count label"),
            count
        )
    }

    // MARK: - Deep Link Handling (012-push-foundation)

    private func handleDeepLink(_ target: NotificationDeepLinkTarget?) {
        guard let target else { return }

        // Parse the dateKey to a Date
        guard let date = HistoryMonthView.dateKeyFormatter.date(from: target.dateKey) else {
            deepLinkTarget = nil
            return
        }

        // Navigate to the month containing the target date
        let targetMonth = HistoryMonthView.startOfMonth(for: date)
        if targetMonth != displayedMonth {
            withAnimation(.easeInOut(duration: 0.25)) {
                displayedMonth = targetMonth
            }
        }

        // Select the date to trigger day detail load
        selectedDate = date

        // Highlight the target slot with a brief animation
        highlightedSlot = target.slot
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(3))
            withAnimation(.easeOut(duration: 0.5)) {
                highlightedSlot = nil
            }
        }

        // Clear the deep link target
        deepLinkTarget = nil
    }
}
