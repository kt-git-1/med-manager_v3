import SwiftUI

struct MedicationSymbolView: View {
    let tint: Color
    var systemImage = "pills.fill"

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [tint.opacity(0.18), tint.opacity(0.08)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .stroke(tint.opacity(0.18), lineWidth: 1)
            Image(systemName: systemImage)
                .font(.system(size: 28, weight: .bold))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(tint)
        }
        .accessibilityHidden(true)
    }
}

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

struct CaregiverPatientSelectionRequiredView: View {
    var systemImage = "person.crop.circle.badge.questionmark"
    let onOpenPatients: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 0)
            VStack(spacing: 16) {
                Image(systemName: systemImage)
                    .font(.system(size: 44))
                    .foregroundStyle(.secondary)
                Text(NSLocalizedString("caregiver.patientSelection.required.title", comment: "Patient selection required title"))
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(NSLocalizedString("caregiver.patientSelection.required.message", comment: "Patient selection required message"))
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                CaregiverPrimaryButton(
                    title: NSLocalizedString("caregiver.patients.open", comment: "Open patients tab"),
                    systemImage: "person.2"
                ) {
                    onOpenPatients()
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("CaregiverPatientSelectionRequiredView")
    }
}

struct CaregiverOnboardingStepRow: View {
    let number: Int
    let title: String
    let systemImage: String
    let tint: Color

    var body: some View {
        HStack(spacing: 12) {
            Text("\(number)")
                .font(.caption.weight(.black))
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(tint, in: Circle())
                .accessibilityHidden(true)

            Image(systemName: systemImage)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(tint)
                .frame(width: 34, height: 34)
                .background(tint.opacity(0.12), in: Circle())
                .accessibilityHidden(true)

            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.elevatedBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityLabel("\(number). \(title)")
    }
}

struct CaregiverDataUnavailableView: View {
    let message: String
    let onRetry: () -> Void
    var onReturnToLogin: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 0)
            VStack(spacing: 16) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(CaregiverUI.red)
                Text(NSLocalizedString("caregiver.dataUnavailable.title", comment: "Caregiver data unavailable title"))
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(message)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                CaregiverPrimaryButton(
                    title: NSLocalizedString("common.retry", comment: "Retry"),
                    systemImage: "arrow.clockwise"
                ) {
                    onRetry()
                }
                if let onReturnToLogin {
                    Button {
                        onReturnToLogin()
                    } label: {
                        Label(
                            NSLocalizedString("caregiver.dataUnavailable.login", comment: "Return to caregiver login"),
                            systemImage: "person.crop.circle"
                        )
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(CaregiverUI.tealDark)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(CaregiverUI.teal.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("CaregiverDataUnavailableLoginButton")
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("CaregiverDataUnavailableView")
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
