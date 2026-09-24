import SwiftUI

@main
struct ConsensusWeatherApp: App {
    @StateObject private var store = Store()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .task { await store.bootstrap() }
                .preferredColorScheme(.dark)
        }
    }
}
