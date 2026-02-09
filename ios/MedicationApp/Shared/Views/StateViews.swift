import SwiftUI

struct LoadingStateView: View {
    let message: String

    var body: some View {
        ProgressView(message)
            .font(.body)
            .foregroundStyle(.secondary)
        .accessibilityLabel(message)
    }
}

struct EmptyStateView: View {
    let title: String
    let message: String

    var body: some View {
        ContentUnavailableView {
            Text(title)
                .font(.title3.weight(.semibold))
        } description: {
            Text(message)
                .font(.body)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 24)
        .accessibilityLabel("\(title) \(message)")
    }
}

struct ErrorStateView: View {
    let message: String

    var body: some View {
        Label(message, systemImage: "exclamationmark.triangle.fill")
            .font(.subheadline)
            .foregroundStyle(.red)
            .accessibilityLabel(message)
    }
}
