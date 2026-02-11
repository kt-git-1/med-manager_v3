import SwiftUI

// ---------------------------------------------------------------------------
// 011-pdf-export: Lock view shown when a free caregiver taps PDF export.
// Follows the HistoryRetentionLockView pattern from 010-history-retention.
// ---------------------------------------------------------------------------

struct PDFExportLockView: View {
    let entitlementStore: EntitlementStore
    let onDismiss: () -> Void

    @State private var showPaywall = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "lock.fill")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)

            Text(NSLocalizedString(
                "pdfexport.lock.title",
                comment: "PDF export lock title"
            ))
            .font(.title3.weight(.semibold))
            .multilineTextAlignment(.center)

            Text(NSLocalizedString(
                "pdfexport.lock.body",
                comment: "PDF export lock body"
            ))
            .font(.body)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 24)

            Spacer()

            VStack(spacing: 12) {
                Button {
                    showPaywall = true
                } label: {
                    Text(NSLocalizedString(
                        "pdfexport.lock.upgrade",
                        comment: "Upgrade button"
                    ))
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                }
                .accessibilityIdentifier("pdfexport.lock.upgrade")

                Button {
                    Task { await entitlementStore.restore() }
                } label: {
                    Text(NSLocalizedString(
                        "pdfexport.lock.restore",
                        comment: "Restore button"
                    ))
                    .font(.subheadline)
                    .foregroundStyle(.tint)
                }
                .accessibilityIdentifier("pdfexport.lock.restore")

                Button {
                    onDismiss()
                } label: {
                    Text(NSLocalizedString(
                        "pdfexport.lock.close",
                        comment: "Close button"
                    ))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
                .accessibilityIdentifier("pdfexport.lock.close")
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
        .accessibilityIdentifier("PDFExportLockView")
        .sheet(isPresented: $showPaywall) {
            PaywallView(entitlementStore: entitlementStore)
        }
    }
}
