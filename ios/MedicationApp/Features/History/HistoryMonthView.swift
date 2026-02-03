import SwiftUI

struct HistoryMonthView: View {
    private static let historyTimeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = historyTimeZone
        calendar.locale = Locale(identifier: "ja_JP")
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
    @StateObject private var viewModel: HistoryViewModel
    @State private var displayedMonth: Date
    @State private var selectedDate: Date?

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: HistoryViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: store),
                sessionStore: store
            )
        )
        _displayedMonth = State(initialValue: HistoryMonthView.startOfMonth(for: Date()))
    }

    var body: some View {
        VStack(spacing: 16) {
            header

            if viewModel.isLoading && viewModel.month == nil {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else {
                calendarGrid
                legend
                HistoryDayDetailView(viewModel: viewModel, selectedDate: selectedDate)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .onAppear {
            loadMonth()
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
        .accessibilityIdentifier("HistoryMonthView")
    }

    private var header: some View {
        HStack(spacing: 12) {
            Button(action: showPreviousMonth) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .padding(8)
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
                    .padding(8)
            }
            .buttonStyle(.plain)
            .disabled(!canGoToNextMonth)
            .accessibilityLabel(NSLocalizedString("history.month.next", comment: "Next month"))
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
        let dayNumber = Self.calendar.component(.day, from: date)
        let isSelected = selectedDate.map { Self.calendar.isDate($0, inSameDayAs: date) } ?? false

        return Button(action: { selectedDate = date }) {
            VStack(spacing: 6) {
                Text("\(dayNumber)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                    .frame(maxWidth: .infinity)
                HStack(spacing: 4) {
                    slotDot(summary?.morning ?? .none)
                    slotDot(summary?.noon ?? .none)
                    slotDot(summary?.evening ?? .none)
                    slotDot(summary?.bedtime ?? .none)
                }
            }
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(isSelected ? Color.accentColor : Color(.secondarySystemBackground))
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func slotDot(_ status: HistorySlotSummaryStatusDTO) -> some View {
        if status != .none {
            Circle()
                .fill(statusColor(status))
                .frame(width: 6, height: 6)
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

    private var legend: some View {
        HStack(spacing: 16) {
            legendItem(color: Color.green, title: NSLocalizedString("history.legend.taken", comment: "Legend taken"))
            legendItem(color: Color.red, title: NSLocalizedString("history.legend.missed", comment: "Legend missed"))
            legendItem(color: Color.gray, title: NSLocalizedString("history.legend.pending", comment: "Legend pending"))
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func legendItem(color: Color, title: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(title)
        }
    }

    private var weekdaySymbols: [String] {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ja_JP")
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
        return Dictionary(
            uniqueKeysWithValues: month.days.map {
                (HistoryMonthView.dateKeyFormatter.string(from: $0.date), $0.slotSummary)
            }
        )
    }

    private var currentMonthStart: Date {
        HistoryMonthView.startOfMonth(for: Date())
    }

    private var minMonth: Date {
        Self.calendar.date(byAdding: .month, value: -2, to: currentMonthStart) ?? currentMonthStart
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
        displayedMonth = Self.calendar.date(from: Self.calendar.dateComponents([.year, .month], from: previous)) ?? previous
    }

    private func showNextMonth() {
        guard canGoToNextMonth,
              let next = Self.calendar.date(byAdding: .month, value: 1, to: displayedMonth) else {
            return
        }
        displayedMonth = Self.calendar.date(from: Self.calendar.dateComponents([.year, .month], from: next)) ?? next
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
}
