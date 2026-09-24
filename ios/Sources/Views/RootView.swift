import SwiftUI

struct RootView: View {
    @EnvironmentObject var store: Store
    @State private var showLocations = false
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            ForecastView()
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { showLocations = true } label: { Image(systemName: "list.bullet") }
                    }
                    ToolbarItem(placement: .principal) {
                        Text(store.activeLocation?.name ?? "Consensus Weather")
                            .font(.headline)
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }
                    }
                }
                .toolbarBackground(.hidden, for: .navigationBar)
        }
        .tint(.white)
        .sheet(isPresented: $showLocations) { LocationsView().environmentObject(store) }
        .sheet(isPresented: $showSettings) { SettingsView().environmentObject(store) }
    }
}
