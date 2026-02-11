import SwiftUI

// ---------------------------------------------------------------------------
// 011-pdf-export: Sheet for period selection with presets, validation, and generate button.
// ---------------------------------------------------------------------------

struct PeriodPickerSheet: View {
    @Bindable var viewModel: PeriodPickerViewModel
    let apiClient: APIClient
    let patientId: String

    @Environment(\.dismiss) private var dismiss
    @State private var shareURL: URL?
    @State private var showShareSheet = false
    @State private var errorMessage: String?
    @State private var showError = false

    var body: some View {
        NavigationStack {
            Form {
                // Preset selector
                Section {
                    Picker(
                        NSLocalizedString("pdfexport.picker.custom", comment: "Period"),
                        selection: $viewModel.selectedPreset
                    ) {
                        ForEach(PeriodPickerViewModel.ReportPeriodPreset.allCases) { preset in
                            Text(preset.displayName).tag(preset)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("PeriodPresetPicker")
                }

                // Custom date pickers
                if viewModel.selectedPreset == .custom {
                    Section {
                        DatePicker(
                            "開始日",
                            selection: $viewModel.customFrom,
                            in: ...viewModel.todayTokyo,
                            displayedComponents: .date
                        )
                        .accessibilityIdentifier("CustomFromDatePicker")

                        DatePicker(
                            "終了日",
                            selection: $viewModel.customTo,
                            in: ...viewModel.todayTokyo,
                            displayedComponents: .date
                        )
                        .accessibilityIdentifier("CustomToDatePicker")
                    }
                }

                // Range display
                Section {
                    let dateFormatter: DateFormatter = {
                        let fmt = DateFormatter()
                        fmt.dateFormat = "yyyy/MM/dd"
                        fmt.timeZone = TimeZone(identifier: "Asia/Tokyo")
                        return fmt
                    }()
                    let fromStr = dateFormatter.string(from: viewModel.effectiveFrom)
                    let toStr = dateFormatter.string(from: viewModel.effectiveTo)
                    let rangeFormatted = String(
                        format: NSLocalizedString("pdfexport.picker.rangeFormat", comment: "Range format"),
                        fromStr, toStr, viewModel.dayCount
                    )
                    Text(rangeFormatted)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("RangeDisplayText")

                    // Validation error
                    if let error = viewModel.validationError {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("ValidationErrorText")
                    }
                }

                // Generate button
                Section {
                    Button {
                        Task { await generate() }
                    } label: {
                        HStack {
                            Spacer()
                            Text(NSLocalizedString("pdfexport.picker.generate", comment: "Generate"))
                                .font(.headline)
                            Spacer()
                        }
                    }
                    .disabled(!viewModel.isValid || viewModel.isGenerating)
                    .accessibilityIdentifier("GeneratePDFButton")
                }
            }
            .navigationTitle(NSLocalizedString("pdfexport.button", comment: "PDF Export"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(NSLocalizedString("pdfexport.lock.close", comment: "Close")) {
                        dismiss()
                    }
                }
            }
            .overlay {
                if viewModel.isGenerating {
                    SchedulingRefreshOverlay()
                }
            }
            .sheet(isPresented: $showShareSheet) {
                if let url = shareURL {
                    ActivityViewRepresentable(activityItems: [url])
                }
            }
            .alert(
                "エラー",
                isPresented: $showError,
                actions: { Button("OK") {} },
                message: { Text(errorMessage ?? "") }
            )
        }
    }

    private func generate() async {
        viewModel.isGenerating = true
        defer { viewModel.isGenerating = false }

        do {
            let url = try await viewModel.generateAndShare(
                apiClient: apiClient,
                patientId: patientId
            )
            shareURL = url
            showShareSheet = true
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }
}

// MARK: - UIActivityViewController Representable

struct ActivityViewRepresentable: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
