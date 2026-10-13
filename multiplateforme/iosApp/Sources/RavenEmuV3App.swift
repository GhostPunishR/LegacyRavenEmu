import SwiftUI
import UIKit
import RavenShared

struct RavenComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosEntryKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct RavenEmuV3App: App {
    var body: some Scene {
        WindowGroup {
            RavenComposeView().ignoresSafeArea(edges: .bottom)
        }
    }
}
