import SwiftUI

enum ToastKind: Equatable {
    case success
    case error
    case info
    case warning

    var iconName: String {
        switch self {
        case .success:
            return "checkmark.circle.fill"
        case .error:
            return "exclamationmark.triangle.fill"
        case .info:
            return "info.circle.fill"
        case .warning:
            return "exclamationmark.circle.fill"
        }
    }

    var tint: Color {
        switch self {
        case .success:
            return Color.green
        case .error:
            return Color.red
        case .info:
            return Color.accentColor
        case .warning:
            return Color.orange
        }
    }

    var defaultDuration: TimeInterval {
        switch self {
        case .error:
            return 3
        default:
            return AppConstants.toastDuration
        }
    }
}

struct AppToast: Identifiable, Equatable {
    let id = UUID()
    let message: String
    let kind: ToastKind
}

@MainActor
final class ToastPresenter: ObservableObject {
    @Published private(set) var toast: AppToast?
    @Published var successFeedbackTrigger = UUID()

    private var hideTask: Task<Void, Never>?

    func show(_ message: String, kind: ToastKind = .success, duration: TimeInterval? = nil) {
        hideTask?.cancel()
        toast = AppToast(message: message, kind: kind)

        if kind == .success {
            successFeedbackTrigger = UUID()
        }

        hideTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(duration ?? kind.defaultDuration))
            await MainActor.run {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.9)) {
                    self?.toast = nil
                }
            }
        }
    }
}

struct ToastOverlayView: View {
    let toast: AppToast

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.kind.iconName)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(toast.kind.tint)

            Text(toast.message)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .glassEffect(.regular, in: .capsule)
        .overlay {
            Capsule()
                .stroke(toast.kind.tint.opacity(0.35), lineWidth: 1)
        }
        .padding(.top, 8)
        .padding(.horizontal, 16)
        .transition(.move(edge: .top).combined(with: .opacity))
        .accessibilityLabel(toast.message)
    }
}
