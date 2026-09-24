import SwiftUI

struct LocationsView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [GeoResult] = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        Task { await store.useCurrentLocation(); dismiss() }
                    } label: {
                        Label(store.t("use_current"), systemImage: "location.fill")
                    }
                }

                if !results.isEmpty {
                    Section(store.t("add")) {
                        ForEach(results) { r in
                            Button {
                                store.add(r); dismiss()
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.name).foregroundStyle(.primary)
                                    if !r.subtitle.isEmpty {
                                        Text(r.subtitle).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }

                Section(store.t("saved_locations")) {
                    ForEach(store.locations) { loc in
                        Button {
                            store.select(loc.id); dismiss()
                        } label: {
                            HStack {
                                if loc.isCurrent {
                                    Image(systemName: "location.fill").font(.caption).foregroundStyle(.secondary)
                                }
                                Text(loc.name).foregroundStyle(.primary)
                                Spacer()
                                if loc.id == store.activeID {
                                    Image(systemName: "checkmark").foregroundStyle(.tint)
                                }
                            }
                        }
                    }
                    .onDelete { offsets in
                        offsets.map { store.locations[$0] }.forEach(store.remove)
                    }
                }
            }
            .searchable(text: $query, prompt: store.t("search_placeholder"))
            .onChange(of: query) { _ in Task { await runSearch() } }
            .navigationTitle(store.t("locations"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(store.t("done")) { dismiss() }
                }
            }
        }
    }

    private func runSearch() async {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard q.count >= 2 else { results = []; return }
        results = (try? await OpenMeteo.geocode(q, lang: store.lang)) ?? []
    }
}
