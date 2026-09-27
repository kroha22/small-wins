import SwiftUI
import UIKit
import SmallWinsShared

struct ComposeRootViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeRootViewController().ignoresSafeArea()
    }
}
