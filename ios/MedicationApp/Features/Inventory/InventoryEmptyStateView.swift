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
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                Button(NSLocalizedString("caregiver.tabs.patients", comment: "Patients tab")) {
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
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
            .padding(.horizontal, 24)
            Spacer(minLength: 0)
        }
        .accessibilityIdentifier("InventoryEmptyStateView")
    }
}
