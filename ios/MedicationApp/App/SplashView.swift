import SwiftUI

struct SplashView: View {
    var onFinished: () -> Void
    @State private var opacity: Double = 0
    @State private var scale: Double = 0.88

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            Image("AppLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 180, height: 180)
                .clipShape(RoundedRectangle(cornerRadius: 40, style: .continuous))
                .shadow(color: .accentColor.opacity(0.25), radius: 24, x: 0, y: 8)
                .shadow(color: .accentColor.opacity(0.10), radius: 48, x: 0, y: 16)
                .scaleEffect(scale)
                .opacity(opacity)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) {
                opacity = 1
                scale = 1.0
            }
            Task {
                try? await Task.sleep(for: .seconds(2))
                onFinished()
            }
        }
    }
}
