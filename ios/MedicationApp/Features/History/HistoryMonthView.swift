import SwiftUI

private enum PatientHistorySimpleStatus {
    case taken
    case pending
    case missed
    case none
}

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
    private static let weekdayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryMonthView.calendar
        formatter.timeZone = HistoryMonthView.historyTimeZone
        formatter.locale = AppConstants.japaneseLocale
        formatter.dateFormat = "E"
        return formatter
    }()
    private static let shortDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryMonthView.calendar
        formatter.timeZone = HistoryMonthView.historyTimeZone
        formatter.locale = AppConstants.japaneseLocale
        formatter.dateFormat = "M/d"
        return formatter
    }()
    private static let patientRecentDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryMonthView.calendar
        formatter.timeZone = HistoryMonthView.historyTimeZone
        formatter.locale = AppConstants.japaneseLocale
        formatter.dateFormat = "M月d日（E）"
        return formatter
    }()

    private let sessionStore: SessionStore
    private let entitlementStore: EntitlementStore?
    private let patientName: String?
    private let apiClient: APIClient
    @StateObject private var viewModel: HistoryViewModel
    @State private var displayedMonth: Date
    @State private var selectedDate: Date?
    @State private var showRetentionLock = false
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var highlightedSlot: NotificationSlot?

    private var isPatientMode: Bool {
        sessionStore.mode == .patient
    }

    init(
        sessionStore: SessionStore? = nil,
        entitlementStore: EntitlementStore? = nil,
        patientName: String? = nil,
        deepLinkTarget: Binding<NotificationDeepLinkTarget?> = .constant(nil)
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.entitlementStore = entitlementStore
        self.patientName = patientName
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
                ZStack {
                    if isPatientMode {
                        PatientScreenBackground()
                    } else {
                        LinearGradient(
                            colors: [Color(red: 0.93, green: 0.98, blue: 1.0), Color(.systemGroupedBackground)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .ignoresSafeArea()
                    }

                    ScrollView {
                        LazyVStack(spacing: 16) {
                            header

                            if viewModel.isLoadingMonth && viewModel.month == nil {
                                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                            } else if let errorMessage = viewModel.monthErrorMessage {
                                errorSection(message: errorMessage, retry: loadMonth)
                            } else if isPatientMode {
                                patientSimpleHistory
                            } else {
                                calendarSection
                                selectedDaySummaryCard
                                HistoryDayDetailView(
                                    viewModel: viewModel,
                                    selectedDate: selectedDate,
                                    highlightedSlot: highlightedSlot,
                                    style: isPatientMode ? .patient : .caregiver
                                )
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                        .padding(.bottom, 120)
                    }
                    .scrollContentBackground(.hidden)
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
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            if isPatientMode {
                PatientHeader(
                    title: NSLocalizedString("patient.readonly.history.title", comment: "History title"),
                    subtitle: NSLocalizedString("patient.history.subtitle", comment: "Patient history subtitle"),
                    systemImage: "clock.fill"
                )
            } else {
                HStack(alignment: .center, spacing: 14) {
                    CaregiverAvatar(name: patientName, systemImage: "calendar")
                        .frame(width: 58, height: 58)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("caregiver.history.title", comment: "Caregiver history title"))
                            .font(.largeTitle.weight(.bold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)
                        Text(patientNameLine)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.78)
                    }
                    Spacer(minLength: 0)
                }
                monthSelector
            }
        }
    }

    private var monthSelector: some View {
        HStack(spacing: 12) {
            Button(action: showPreviousMonth) {
                Image(systemName: "chevron.left")
                    .font(.headline.weight(.bold))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canGoToPreviousMonth)
            .accessibilityLabel(NSLocalizedString("history.month.previous", comment: "Previous month"))

            Spacer()

            Text(HistoryMonthView.monthFormatter.string(from: displayedMonth))
                .font(.title3.weight(.bold))
                .foregroundStyle(.primary)

            Spacer()

            Button(action: showNextMonth) {
                Image(systemName: "chevron.right")
                    .font(.headline.weight(.bold))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canGoToNextMonth)
            .accessibilityLabel(NSLocalizedString("history.month.next", comment: "Next month"))
        }
    }

    private var patientSimpleHistory: some View {
        VStack(alignment: .leading, spacing: 16) {
            PatientCard(accent: PatientUI.teal) {
                VStack(alignment: .center, spacing: 18) {
                    Text(NSLocalizedString("patient.history.week.title", comment: "Patient week title"))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)

                    VStack(spacing: 2) {
                        Text(String(format: NSLocalizedString("patient.history.week.count", comment: "Patient week count"), weeklyRecordedCount))
                            .font(.system(size: 50, weight: .bold, design: .rounded))
                            .foregroundStyle(PatientUI.teal)
                            .minimumScaleFactor(0.72)
                            .lineLimit(1)
                        Text(NSLocalizedString("patient.history.week.recorded", comment: "Patient week recorded"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(PatientUI.teal)
                    }

                    HStack(spacing: 8) {
                        ForEach(lastSevenDates, id: \.self) { date in
                            patientWeekDayView(for: date)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            VStack(alignment: .leading, spacing: 12) {
                Text(NSLocalizedString("patient.history.recent.title", comment: "Patient recent title"))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)

                ForEach(recentHistoryDates, id: \.self) { date in
                    PatientCard {
                        HStack(alignment: .center, spacing: 14) {
                            Image(systemName: patientHistoryIconName(for: date))
                                .font(.system(size: 26, weight: .bold))
                                .foregroundStyle(patientHistoryAccent(for: date))
                                .frame(width: 54, height: 54)
                                .background(patientHistoryAccent(for: date).opacity(0.12), in: Circle())

                            VStack(alignment: .leading, spacing: 4) {
                                Text(patientHistoryDateTitle(for: date))
                                    .font(.title3.weight(.bold))
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.76)
                                Text(patientHistorySubtitle(for: date))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }

                            Spacer(minLength: 0)

                            PatientStatusPill(
                                text: patientHistoryStatusText(for: date),
                                color: patientHistoryStatusColor(for: date),
                                systemImage: patientHistoryStatusIcon(for: date)
                            )
                        }
                    }
                }
            }
        }
    }

    private func patientWeekDayView(for date: Date) -> some View {
        VStack(spacing: 6) {
            Text(Self.weekdayFormatter.string(from: date))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
            ZStack {
                Circle()
                    .fill(patientHistoryStatusColor(for: date).opacity(patientHistoryStatus(for: date) == .none ? 0.12 : 1))
                    .frame(width: 34, height: 34)
                Image(systemName: patientHistoryWeekIcon(for: date))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(patientHistoryStatus(for: date) == .none ? Color.secondary : Color.white)
            }
            Text(Self.shortDateFormatter.string(from: date))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
    }

    private var lastSevenDates: [Date] {
        (-6...0).compactMap { offset in
            Self.calendar.date(byAdding: .day, value: offset, to: Date())
        }
    }

    private var recentHistoryDates: [Date] {
        [0, -1].compactMap { offset in
            Self.calendar.date(byAdding: .day, value: offset, to: Date())
        }
    }

    private var weeklyRecordedCount: Int {
        lastSevenDates.filter { patientHistoryStatus(for: $0) == .taken }.count
    }

    private func patientHistoryStatus(for date: Date) -> PatientHistorySimpleStatus {
        let dateKey = HistoryMonthView.dateKeyFormatter.string(from: date)
        let summary = summariesByDate[dateKey]
        let statuses = [
            summary?.morning ?? .none,
            summary?.noon ?? .none,
            summary?.evening ?? .none,
            summary?.bedtime ?? .none
        ].filter { $0 != .none }
        let prnCount = prnCountByDate[dateKey] ?? 0

        if statuses.contains(.missed) {
            return .missed
        }
        if statuses.contains(.pending) {
            return .pending
        }
        if statuses.contains(.taken) || prnCount > 0 {
            return .taken
        }
        return .none
    }

    private func patientHistoryStatusText(for date: Date) -> String {
        switch patientHistoryStatus(for: date) {
        case .taken:
            return NSLocalizedString("patient.history.status.done", comment: "Patient history done")
        case .pending:
            return NSLocalizedString("patient.history.status.pending", comment: "Patient history pending")
        case .missed:
            return NSLocalizedString("patient.history.status.missed", comment: "Patient history missed")
        case .none:
            return NSLocalizedString("patient.history.status.none", comment: "Patient history none")
        }
    }

    private func patientHistoryStatusColor(for date: Date) -> Color {
        switch patientHistoryStatus(for: date) {
        case .taken:
            return PatientUI.teal
        case .pending:
            return PatientUI.red
        case .missed:
            return PatientUI.red
        case .none:
            return Color.gray
        }
    }

    private func patientHistoryStatusIcon(for date: Date) -> String? {
        switch patientHistoryStatus(for: date) {
        case .taken:
            return "checkmark"
        case .missed:
            return "exclamationmark"
        case .pending, .none:
            return nil
        }
    }

    private func patientHistoryWeekIcon(for date: Date) -> String {
        switch patientHistoryStatus(for: date) {
        case .taken:
            return "checkmark"
        case .pending, .missed:
            return "exclamationmark"
        case .none:
            return "minus"
        }
    }

    private func patientHistoryIconName(for date: Date) -> String {
        switch patientHistoryStatus(for: date) {
        case .taken:
            return "checkmark.circle.fill"
        case .pending:
            return "sun.max.fill"
        case .missed:
            return "exclamationmark.triangle.fill"
        case .none:
            return "calendar"
        }
    }

    private func patientHistoryAccent(for date: Date) -> Color {
        switch patientHistoryStatus(for: date) {
        case .taken:
            return PatientUI.teal
        case .pending:
            return PatientUI.orange
        case .missed:
            return PatientUI.red
        case .none:
            return PatientUI.blue
        }
    }

    private func patientHistoryDateTitle(for date: Date) -> String {
        if Self.calendar.isDateInToday(date) {
            return String(
                format: NSLocalizedString("patient.history.today.format", comment: "Patient history today"),
                Self.patientRecentDateFormatter.string(from: date)
            )
        }
        if Self.calendar.isDateInYesterday(date) {
            return String(
                format: NSLocalizedString("patient.history.yesterday.format", comment: "Patient history yesterday"),
                Self.patientRecentDateFormatter.string(from: date)
            )
        }
        return Self.patientRecentDateFormatter.string(from: date)
    }

    private func patientHistorySubtitle(for date: Date) -> String {
        let dateKey = HistoryMonthView.dateKeyFormatter.string(from: date)
        guard let summary = summariesByDate[dateKey] else {
            if (prnCountByDate[dateKey] ?? 0) > 0 {
                return NSLocalizedString("patient.history.subtitle.prnOnly", comment: "PRN only")
            }
            return NSLocalizedString("patient.history.subtitle.noSchedule", comment: "No schedule")
        }
        let slots: [(String, HistorySlotSummaryStatusDTO)] = [
            (NSLocalizedString("history.slot.morning", comment: "Morning"), summary.morning),
            (NSLocalizedString("history.slot.noon", comment: "Noon"), summary.noon),
            (NSLocalizedString("history.slot.evening", comment: "Evening"), summary.evening),
            (NSLocalizedString("history.slot.bedtime", comment: "Bedtime"), summary.bedtime)
        ]
        let activeSlots = slots
            .filter { $0.1 != .none }
            .map(\.0)
        if activeSlots.isEmpty {
            return NSLocalizedString("patient.history.subtitle.noSchedule", comment: "No schedule")
        }
        return String(
            format: NSLocalizedString("patient.history.subtitle.slots", comment: "Slot summary"),
            activeSlots.joined(separator: "・")
        )
    }

    private var calendarSection: some View {
        Group {
            if isPatientMode {
                PatientCard {
                    calendarContent
                }
            } else {
                CaregiverCard {
                    calendarContent
                }
            }
        }
        .accessibilityIdentifier("HistoryCalendarSection")
    }

    private var calendarContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(NSLocalizedString("history.calendar.title", comment: "History calendar title"))
                        .font((isPatientMode ? Font.title3 : Font.headline).weight(.bold))
                    Text(NSLocalizedString(isPatientMode ? "patient.history.calendar.message" : "history.calendar.message", comment: "History calendar message"))
                        .font(isPatientMode ? .body.weight(.semibold) : .subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            calendarGrid
            legend
        }
    }

    private var selectedDaySummaryCard: some View {
        Group {
            if isPatientMode {
                PatientCard {
                    selectedDaySummaryContent
                }
            } else {
                CaregiverCard {
                    selectedDaySummaryContent
                }
            }
        }
        .accessibilityIdentifier("HistorySelectedDaySummary")
    }

    private var selectedDaySummaryContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(NSLocalizedString("history.selected.title", comment: "Selected day section title"))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                    Text(selectedDateTitle)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)
                }
                Spacer()
                if selectedPrnCount > 0 {
                    historyStatusPill(
                        text: prnCountLabel(count: selectedPrnCount),
                        color: historyPrnColor,
                        systemImage: "cross.case.fill"
                    )
                }
            }
            Text(selectedSummaryText)
                .font(.title2.weight(.bold))
                .foregroundStyle(.primary)
            Text(selectedSummaryHelpText)
                .font(isPatientMode ? .body.weight(.semibold) : .subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if selectedTotalCount > 0 {
                HStack(spacing: 8) {
                    historyStatusPill(
                        text: String(format: NSLocalizedString("caregiver.history.summary.taken", comment: "Taken count"), selectedTakenCount),
                        color: historyTakenColor,
                        systemImage: "checkmark.circle.fill"
                    )
                    historyStatusPill(
                        text: String(format: NSLocalizedString("caregiver.history.summary.pending", comment: "Pending count"), selectedPendingCount),
                        color: selectedPendingCount > 0 ? historyPendingColor : .gray,
                        systemImage: "clock.fill"
                    )
                    if selectedMissedCount > 0 {
                        historyStatusPill(
                            text: String(format: NSLocalizedString("caregiver.history.summary.missed", comment: "Missed count"), selectedMissedCount),
                            color: historyMissedColor,
                            systemImage: "exclamationmark.triangle.fill"
                        )
                    }
                }
            }
        }
    }

    private var calendarGrid: some View {
        VStack(spacing: 10) {
            HStack(spacing: 0) {
                ForEach(weekdaySymbols, id: \.self) { symbol in
                    Text(symbol)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 8) {
                ForEach(dayCells.indices, id: \.self) { index in
                    if let date = dayCells[index] {
                        dayCell(for: date)
                    } else {
                        Color.clear
                            .frame(height: 54)
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
        let hasAnyHistory = summary != nil || (prnCount ?? 0) > 0

        return Button(action: { selectedDate = date }) {
            VStack(spacing: 6) {
                Text("\(dayNumber)")
                    .font(isPatientMode ? .headline.weight(.bold) : .subheadline.weight(.semibold))
                    .foregroundStyle(isSelected ? Color.white : (hasAnyHistory ? Color.primary : Color.secondary))
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
                .frame(height: 8)
            }
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, minHeight: isPatientMode ? 58 : 54)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(isSelected ? historySelectedColor : (hasAnyHistory ? Color.primary.opacity(0.05) : Color.clear))
            )
            .overlay {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isSelected ? historySelectedDarkColor.opacity(0.40) : Color.primary.opacity(hasAnyHistory ? 0.08 : 0.04), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(dayCellAccessibilityLabel(day: dayNumber, summary: summary, prnCount: prnCount))
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
            return historyTakenColor
        case .missed:
            return historyMissedColor
        case .pending:
            return Color.gray
        case .none:
            return Color.clear
        }
    }

    private func prnDot(count: Int) -> some View {
        Circle()
            .fill(historyPrnColor)
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
        VStack(alignment: .leading, spacing: 10) {
            Text(NSLocalizedString("history.legend.help", comment: "Legend help"))
                .font(.caption)
                .foregroundStyle(.secondary)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], alignment: .leading, spacing: 8) {
                legendItem(color: historyTakenColor, title: NSLocalizedString("history.legend.taken", comment: "Legend taken"))
                legendItem(color: historyMissedColor, title: NSLocalizedString("history.legend.missed", comment: "Legend missed"))
                legendItem(color: Color.gray, title: NSLocalizedString("history.legend.pending", comment: "Legend pending"))
                legendItem(color: historyPrnColor, title: NSLocalizedString("history.legend.prn", comment: "Legend PRN"))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("HistoryLegend")
    }

    private func legendItem(color: Color, title: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .accessibilityLabel(title)
    }

    @ViewBuilder
    private func historyStatusPill(text: String, color: Color, systemImage: String) -> some View {
        if isPatientMode {
            PatientStatusPill(text: text, color: color, systemImage: systemImage)
        } else {
            CaregiverStatusPill(text: text, color: color, systemImage: systemImage)
        }
    }

    private var historyTakenColor: Color { isPatientMode ? PatientUI.teal : CaregiverUI.teal }
    private var historyPendingColor: Color { isPatientMode ? PatientUI.orange : CaregiverUI.orange }
    private var historyMissedColor: Color { isPatientMode ? PatientUI.red : CaregiverUI.red }
    private var historyPrnColor: Color { isPatientMode ? PatientUI.indigo : Color.purple }
    private var historySelectedColor: Color { isPatientMode ? PatientUI.teal : CaregiverUI.teal }
    private var historySelectedDarkColor: Color { isPatientMode ? PatientUI.tealDark : CaregiverUI.tealDark }

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

    private var selectedDateKey: String? {
        selectedDate.map { HistoryMonthView.dateKeyFormatter.string(from: $0) }
    }

    private var selectedSummary: HistorySlotSummaryDTO? {
        guard let selectedDateKey else { return nil }
        return summariesByDate[selectedDateKey]
    }

    private var selectedStatuses: [HistorySlotSummaryStatusDTO] {
        guard let selectedSummary else { return [] }
        return [selectedSummary.morning, selectedSummary.noon, selectedSummary.evening, selectedSummary.bedtime]
            .filter { $0 != .none }
    }

    private var selectedTotalCount: Int { selectedStatuses.count }
    private var selectedTakenCount: Int { selectedStatuses.filter { $0 == .taken }.count }
    private var selectedPendingCount: Int { selectedStatuses.filter { $0 == .pending }.count }
    private var selectedMissedCount: Int { selectedStatuses.filter { $0 == .missed }.count }
    private var selectedPrnCount: Int {
        guard let selectedDateKey else { return 0 }
        return prnCountByDate[selectedDateKey] ?? 0
    }

    private var selectedSummaryText: String {
        if selectedTotalCount == 0 {
            return NSLocalizedString("history.selected.noSchedule", comment: "No selected schedule")
        }
        return String(
            format: NSLocalizedString("caregiver.history.summary.format", comment: "History summary"),
            selectedTakenCount,
            selectedTotalCount
        )
    }

    private var selectedSummaryHelpText: String {
        if selectedTotalCount == 0 && selectedPrnCount == 0 {
            return NSLocalizedString("history.selected.noScheduleHelp", comment: "No selected schedule help")
        }
        if selectedMissedCount > 0 {
            return NSLocalizedString("history.selected.missedHelp", comment: "Missed help")
        }
        if selectedPendingCount > 0 {
            return NSLocalizedString("history.selected.pendingHelp", comment: "Pending help")
        }
        return NSLocalizedString("history.selected.completeHelp", comment: "Complete help")
    }

    private var selectedDateTitle: String {
        guard let selectedDate else {
            return NSLocalizedString("caregiver.history.summary.noSelection", comment: "No date selected")
        }
        let formatter = DateFormatter()
        formatter.locale = AppConstants.japaneseLocale
        formatter.calendar = Self.calendar
        formatter.timeZone = Self.historyTimeZone
        formatter.dateFormat = "M月d日（E）"
        return formatter.string(from: selectedDate)
    }

    private var prnCountByDate: [String: Int] {
        viewModel.month?.prnCountByDay ?? [:]
    }

    private var patientNameLine: String {
        guard let patientName, !patientName.isEmpty else {
            return NSLocalizedString("caregiver.common.patient.none", comment: "No patient selected")
        }
        return String(format: NSLocalizedString("caregiver.common.patient.format", comment: "Patient name format"), patientName)
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

    private func dayCellAccessibilityLabel(day: Int, summary: HistorySlotSummaryDTO?, prnCount: Int?) -> String {
        var parts = ["\(day)日"]
        if let summary {
            let slots: [(String, HistorySlotSummaryStatusDTO)] = [
                (slotLabel(for: "morning"), summary.morning),
                (slotLabel(for: "noon"), summary.noon),
                (slotLabel(for: "evening"), summary.evening),
                (slotLabel(for: "bedtime"), summary.bedtime)
            ]
            parts.append(contentsOf: slots.compactMap { slot, status in
                status == .none ? nil : "\(slot) \(statusLabel(for: status))"
            })
        }
        if let prnCount, prnCount > 0 {
            parts.append(prnCountLabel(count: prnCount))
        }
        if parts.count == 1 {
            parts.append(NSLocalizedString("history.selected.noSchedule", comment: "No selected schedule"))
        }
        return parts.joined(separator: "、")
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
