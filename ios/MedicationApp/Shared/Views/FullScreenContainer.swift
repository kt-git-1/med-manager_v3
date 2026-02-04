import SwiftUI
import UIKit

struct FullScreenContainer<Content: View>: View {
    private let content: Content
    private let overlay: AnyView?

    init(@ViewBuilder content: () -> Content, overlay: AnyView? = nil) {
        self.content = content()
        self.overlay = overlay
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color(.systemGroupedBackground)
                    .ignoresSafeArea()
                content
                if let overlay {
                    overlay
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
        }
        .onAppear {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first else {
                return
            }
            let size = scene.screen.bounds.size
            scene.sizeRestrictions?.minimumSize = size
            scene.sizeRestrictions?.maximumSize = size
        }
    }
}
