import SwiftUI

struct InventoryEmptyStateView: View {
    let onOpenPatients: () -> Void

    var body: some View {
        CaregiverPatientSelectionRequiredView(
            systemImage: "shippingbox",
            onOpenPatients: onOpenPatients
        )
        .accessibilityIdentifier("InventoryEmptyStateView")
    }
}
