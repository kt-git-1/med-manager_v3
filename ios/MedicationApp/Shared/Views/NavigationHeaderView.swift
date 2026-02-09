import SwiftUI

struct NavigationHeaderView: ToolbarContent {
    let icon: String
    let title: String

    var body: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 26))
                    .foregroundStyle(Color.accentColor)
                    .symbolRenderingMode(.hierarchical)
                Text(title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
                Spacer(minLength: 0)
            }
        }
    }
}
