import SwiftUI

struct LoadingStateView: View {
    let message: String

    var body: some View {
        VStack(spacing: 14) {
            ProgressView()
                .controlSize(.large)
            Text(message)
                .font(.title3.weight(.semibold))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .accessibilityLabel(message)
    }
}

struct EmptyStateView: View {
    let title: String
    let message: String

    var body: some View {
        ContentUnavailableView {
            Text(title)
                .font(.title2.weight(.bold))
        } description: {
            Text(message)
                .font(.title3)
                .lineSpacing(4)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 24)
        .accessibilityLabel("\(title) \(message)")
    }
}

struct CompactEmptyStateView: View {
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
            Text(message)
                .font(.title3)
                .foregroundStyle(.secondary)
                .lineSpacing(4)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
        .accessibilityLabel("\(title) \(message)")
    }
}

struct CaregiverNoPatientEmptyStateView: View {
    let onCreatePatient: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 0)
            VStack(spacing: 16) {
                Image(systemName: "person.crop.circle.badge.plus")
                    .font(.system(size: 44))
                    .foregroundStyle(.secondary)
                CompactEmptyStateView(
                    title: NSLocalizedString("caregiver.patients.empty.title", comment: "Empty patients title"),
                    message: NSLocalizedString("caregiver.patients.empty.message", comment: "Empty patients message")
                )
                CaregiverPrimaryButton(
                    title: NSLocalizedString("caregiver.patients.register", comment: "Register first patient"),
                    systemImage: "person.crop.circle.badge.plus"
                ) {
                    onCreatePatient()
                }
                .accessibilityIdentifier("caregiver.patients.register")
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("CaregiverNoPatientEmptyStateView")
    }
}

struct ErrorStateView: View {
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(.red)
            Text(message)
                .font(.headline.weight(.semibold))
                .foregroundStyle(.red)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(22)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
        .padding(.horizontal, 16)
        .accessibilityLabel(message)
    }
}
