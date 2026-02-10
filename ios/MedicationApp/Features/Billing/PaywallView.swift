import SwiftUI
import StoreKit

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    let entitlementStore: EntitlementStore
    @State private var viewModel: PaywallViewModel

    init(entitlementStore: EntitlementStore) {
        self.entitlementStore = entitlementStore
        self._viewModel = State(wrappedValue: PaywallViewModel(entitlementStore: entitlementStore))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                contentView
                    .navigationTitle(NSLocalizedString("billing.paywall.title", comment: "Paywall title"))
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button(NSLocalizedString("billing.paywall.close", comment: "Close")) {
                                dismiss()
                            }
                            .accessibilityIdentifier("billing.paywall.close")
                        }
                    }

                if entitlementStore.isRefreshing {
                    SchedulingRefreshOverlay()
                }
            }
        }
        .task {
            await viewModel.loadProduct()
        }
        .interactiveDismissDisabled(entitlementStore.isRefreshing)
    }

    @ViewBuilder
    private var contentView: some View {
        if let error = viewModel.loadError {
            errorView(message: error)
        } else if viewModel.isLoadingProduct {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let product = viewModel.product {
            productView(product: product)
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func productView(product: Product) -> some View {
        VStack(spacing: 24) {
            Spacer()

            VStack(spacing: 12) {
                Image(systemName: "crown.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(.yellow)

                Text(product.displayPrice)
                    .font(.largeTitle.bold())

                Text(NSLocalizedString("billing.paywall.description", comment: "Description"))
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Spacer()

            VStack(spacing: 12) {
                Button {
                    Task { await viewModel.purchase() }
                } label: {
                    Text(NSLocalizedString("billing.paywall.purchase", comment: "Purchase"))
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                }
                .accessibilityIdentifier("billing.paywall.purchase")

                Button {
                    Task { await viewModel.restore() }
                } label: {
                    Text(NSLocalizedString("billing.paywall.restore", comment: "Restore"))
                        .font(.subheadline)
                        .foregroundStyle(.tint)
                }
                .accessibilityIdentifier("billing.paywall.restore")
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
    }

    private func errorView(message: String) -> some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)

            Text(message)
                .font(.body)
                .foregroundStyle(.secondary)

            Button {
                viewModel.retryLoad()
            } label: {
                Text(NSLocalizedString("billing.paywall.retry", comment: "Retry"))
                    .font(.headline)
                    .foregroundStyle(.tint)
            }
            .accessibilityIdentifier("billing.paywall.retry")

            Spacer()
        }
    }
}
