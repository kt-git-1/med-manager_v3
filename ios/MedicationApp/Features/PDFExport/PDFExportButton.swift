import SwiftUI

// ---------------------------------------------------------------------------
// 011-pdf-export: Toolbar button for PDF export.
// Visible only for caregivers. Checks FeatureGate.pdfExport for premium gate.
// ---------------------------------------------------------------------------

struct PDFExportButton: View {
    let entitlementStore: EntitlementStore
    let sessionStore: SessionStore
    let patientId: String
    let apiClient: APIClient

    @State private var showPicker = false
    @State private var showLock = false
    @State private var showOverlay = false
    @State private var viewModel = PeriodPickerViewModel()

    var body: some View {
        if sessionStore.mode == .caregiver {
            Button {
                handleTap()
            } label: {
                Image(systemName: "square.and.arrow.up")
            }
            .accessibilityIdentifier("PDFExportButton")
            .sheet(isPresented: $showPicker) {
                PeriodPickerSheet(
                    viewModel: viewModel,
                    apiClient: apiClient,
                    patientId: patientId
                )
            }
            .sheet(isPresented: $showLock) {
                PDFExportLockView(
                    entitlementStore: entitlementStore,
                    onDismiss: { showLock = false }
                )
            }
            .overlay {
                if showOverlay {
                    SchedulingRefreshOverlay()
                }
            }
        }
    }

    private func handleTap() {
        if FeatureGate.isUnlocked(.pdfExport, for: entitlementStore.state) {
            // Premium — show period picker
            viewModel = PeriodPickerViewModel()
            showPicker = true
        } else if entitlementStore.state == .unknown {
            // Unknown — refresh entitlement and retry
            showOverlay = true
            Task {
                await entitlementStore.refresh()
                showOverlay = false
                if FeatureGate.isUnlocked(.pdfExport, for: entitlementStore.state) {
                    viewModel = PeriodPickerViewModel()
                    showPicker = true
                } else {
                    showLock = true
                }
            }
        } else {
            // Free — show lock view
            showLock = true
        }
    }
}
