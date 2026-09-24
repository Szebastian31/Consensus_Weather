import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(store.t("units")) {
                    Picker(store.t("temperature"), selection: $store.tempUnit) {
                        ForEach(TempUnit.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                    Picker(store.t("wind_speed"), selection: $store.windUnit) {
                        ForEach(WindUnit.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                    Picker(store.t("pressure_label"), selection: $store.pressUnit) {
                        ForEach(PressUnit.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                }

                Section(store.t("language")) {
                    Picker(store.t("language"), selection: $store.lang) {
                        ForEach(L10n.supported, id: \.self) { Text($0.uppercased()).tag($0) }
                    }
                }

                Section(store.t("notifications")) {
                    Toggle(store.t("daily_notification"), isOn: $store.notifyOn)
                    if store.notifyOn {
                        DatePicker(store.t("notify_time"),
                                   selection: $store.notifyTime,
                                   displayedComponents: .hourAndMinute)
                    }
                }

                Section {
                    Text("Weather data by Open-Meteo.com")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(store.t("settings"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(store.t("done")) { dismiss() }
                }
            }
        }
    }
}
