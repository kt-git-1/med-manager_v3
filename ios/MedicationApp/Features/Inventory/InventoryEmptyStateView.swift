import SwiftUI

struct InventoryEmptyStateView: View {
    let onOpenPatients: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 0)
            VStack(spacing: 12) {
                Text(NSLocalizedString("caregiver.inventory.empty.title", comment: "Inventory empty title"))
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(NSLocalizedString("caregiver.inventory.empty.message", comment: "Inventory empty message"))
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab")) {
                    onOpenPatients()
                }
                .buttonStyle(.borderedProminent)
                .font(.headline)
                .padding(.top, 4)
                .accessibilityIdentifier("InventoryEmptyStateCTA")
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("InventoryEmptyStateView")
    }
}
