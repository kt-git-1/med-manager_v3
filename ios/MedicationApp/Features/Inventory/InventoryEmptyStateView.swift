import SwiftUI

struct InventoryEmptyStateView: View {
    let onOpenPatients: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 0)
            VStack(spacing: 16) {
                Image(systemName: "archivebox")
                    .font(.system(size: 44))
                    .foregroundStyle(.secondary)
                Text(NSLocalizedString("caregiver.inventory.empty.title", comment: "Inventory empty title"))
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(NSLocalizedString("caregiver.inventory.empty.message", comment: "Inventory empty message"))
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button {
                    onOpenPatients()
                } label: {
                    Text(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab"))
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                }
                .accessibilityIdentifier("InventoryEmptyStateCTA")
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("InventoryEmptyStateView")
    }
}
