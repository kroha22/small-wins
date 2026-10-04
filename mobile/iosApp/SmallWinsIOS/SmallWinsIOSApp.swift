import SwiftUI

@main
struct SmallWinsIOSApp: App {
    init() {
        if ProcessInfo.processInfo.arguments.contains("--uitesting-reset") {
            UserDefaults.standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier!)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
