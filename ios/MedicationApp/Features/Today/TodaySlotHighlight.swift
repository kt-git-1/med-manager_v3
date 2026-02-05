import SwiftUI

struct TodaySlotHighlight: ViewModifier {
    let isHighlighted: Bool

    func body(content: Content) -> some View {
        content
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.accentColor.opacity(isHighlighted ? 0.75 : 0.0), lineWidth: 3)
                    .shadow(color: Color.accentColor.opacity(isHighlighted ? 0.35 : 0.0), radius: 12)
            )
            .animation(.easeInOut(duration: 0.4), value: isHighlighted)
    }
}

extension View {
    func todaySlotHighlight(_ isHighlighted: Bool) -> some View {
        modifier(TodaySlotHighlight(isHighlighted: isHighlighted))
    }
}

extension NotificationSlot {
    static func from(date: Date, timeZone: TimeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current) -> NotificationSlot? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let components = calendar.dateComponents([.hour, .minute], from: date)
        switch (components.hour, components.minute) {
        case (8, 0):
            return .morning
        case (12, 0):
            return .noon
        case (19, 0):
            return .evening
        case (22, 0):
            return .bedtime
        default:
            return nil
        }
    }
}
