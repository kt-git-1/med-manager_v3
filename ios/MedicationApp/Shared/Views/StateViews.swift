import SwiftUI

struct LoadingStateView: View {
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            ProgressView()
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
        }
        .accessibilityLabel(message)
    }
}

struct EmptyStateView: View {
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.title3.weight(.semibold))
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 24)
        .accessibilityLabel("\(title) \(message)")
    }
}

struct ErrorStateView: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.subheadline)
            .foregroundColor(.red)
            .accessibilityLabel(message)
    }
}
