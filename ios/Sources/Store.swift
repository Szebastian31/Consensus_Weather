import Foundation
import SwiftUI
import CoreLocation

// MARK: - Units

enum TempUnit: String, CaseIterable, Codable {
    case c, f
    var label: String { self == .c ? "°C" : "°F" }
    func value(_ celsius: Double) -> Double { self == .f ? celsius * 9 / 5 + 32 : celsius }
}

enum WindUnit: String, CaseIterable, Codable {
    case kmh, ms, mph, kn
    var label: String {
        switch self {
        case .kmh: return "km/h"
        case .ms:  return "m/s"
        case .mph: return "mph"
        case .kn:  return "kn"
        }
    }
    func value(_ kmh: Double) -> Double {
        switch self {
        case .kmh: return kmh
        case .ms:  return kmh / 3.6
        case .mph: return kmh * 0.621371
        case .kn:  return kmh * 0.539957
        }
    }
    var decimals: Int { self == .ms ? 1 : 0 }
}

enum PressUnit: String, CaseIterable, Codable {
    case hpa, inhg, mmhg
    var label: String {
        switch self {
        case .hpa:  return "hPa"
        case .inhg: return "inHg"
        case .mmhg: return "mmHg"
        }
    }
    func value(_ hpa: Double) -> Double {
        switch self {
        case .hpa:  return hpa
        case .inhg: return hpa * 0.02953
        case .mmhg: return hpa * 0.750062
        }
    }
    var decimals: Int { self == .inhg ? 2 : 0 }
}

// MARK: - Store

@MainActor
final class Store: ObservableObject {
    @Published var locations: [SavedLocation] = []
    @Published var activeID: UUID?
    @Published var bundle: WeatherBundle?
    @Published var loading = false
    @Published var errorText: String?

    @Published var tempUnit: TempUnit { didSet { persistPrefs() } }
    @Published var windUnit: WindUnit { didSet { persistPrefs() } }
    @Published var pressUnit: PressUnit { didSet { persistPrefs() } }
    @Published var lang: String { didSet { persistPrefs() } }
    @Published var notifyOn: Bool { didSet { persistPrefs(); Task { await syncNotifications() } } }
    @Published var notifyTime: Date { didSet { persistPrefs(); Task { await syncNotifications() } } }

    private let locator = LocationManager()
    private let d = UserDefaults.standard

    init() {
        tempUnit  = TempUnit(rawValue: d.string(forKey: "cw_temp") ?? "c") ?? .c
        windUnit  = WindUnit(rawValue: d.string(forKey: "cw_wind") ?? "kmh") ?? .kmh
        pressUnit = PressUnit(rawValue: d.string(forKey: "cw_press") ?? "hpa") ?? .hpa
        lang      = d.string(forKey: "cw_lang") ?? L10n.deviceLang()
        notifyOn  = d.bool(forKey: "cw_notify")

        let h = d.object(forKey: "cw_nh") as? Int ?? 8
        let m = d.object(forKey: "cw_nm") as? Int ?? 0
        notifyTime = Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: Date()) ?? Date()

        loadLocations()
    }

    // MARK: Lifecycle

    func bootstrap() async {
        if locations.isEmpty {
            await useCurrentLocation()
        } else {
            await refresh()
        }
    }

    func refresh() async {
        guard let loc = activeLocation else { return }
        loading = true
        errorText = nil
        do {
            bundle = try await OpenMeteo.fetch(lat: loc.lat, lon: loc.lon)
            await syncNotifications()
        } catch {
            errorText = t("fetch_error")
        }
        loading = false
    }

    // MARK: Locations

    var activeLocation: SavedLocation? {
        locations.first { $0.id == activeID } ?? locations.first
    }

    func useCurrentLocation() async {
        loading = true
        errorText = nil
        guard let coord = await locator.currentCoordinate() else {
            loading = false
            if locations.isEmpty { errorText = t("loc_denied") }
            return
        }
        let name = (try? await OpenMeteo.reverse(lat: coord.latitude, lon: coord.longitude, lang: lang)) ?? ""
        let loc = SavedLocation(name: name.isEmpty ? t("my_location") : name,
                                lat: coord.latitude, lon: coord.longitude, isCurrent: true)
        locations.removeAll { $0.isCurrent }
        locations.insert(loc, at: 0)
        activeID = loc.id
        saveLocations()
        await refresh()
    }

    func add(_ result: GeoResult) {
        let loc = SavedLocation(name: result.name, lat: result.latitude, lon: result.longitude)
        locations.append(loc)
        activeID = loc.id
        saveLocations()
        Task { await refresh() }
    }

    func select(_ id: UUID) {
        activeID = id
        saveLocations()
        Task { await refresh() }
    }

    func remove(_ loc: SavedLocation) {
        locations.removeAll { $0.id == loc.id }
        if activeID == loc.id { activeID = locations.first?.id }
        saveLocations()
        Task { await refresh() }
    }

    // MARK: Formatting

    func t(_ key: String) -> String { L10n.t(lang, key) }
    func tf(_ key: String, _ vars: [String: String]) -> String { L10n.tf(lang, key, vars) }
    func label(for c: Condition) -> String { t(c.labelKey) }

    /// Temperature as an integer with a degree sign (optionally the unit letter).
    func tempStr(_ celsius: Double, unit: Bool = false) -> String {
        let v = Int(tempUnit.value(celsius).rounded())
        return unit ? "\(v)\(tempUnit.label)" : "\(v)°"
    }

    func windStr(_ kmh: Double) -> String {
        let v = windUnit.value(kmh)
        return windUnit.decimals > 0 ? String(format: "%.\(windUnit.decimals)f", v) : "\(Int(v.rounded()))"
    }

    func pressStr(_ hpa: Double) -> String {
        let v = pressUnit.value(hpa)
        return pressUnit.decimals > 0 ? String(format: "%.\(pressUnit.decimals)f", v) : "\(Int(v.rounded()))"
    }

    func hourLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "HH"
        f.timeZone = TimeZone(secondsFromGMT: bundle?.utcOffsetSeconds ?? 0)
        return f.string(from: date)
    }

    func weekdayLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "EEE"
        f.locale = Locale(identifier: lang)
        f.timeZone = TimeZone(secondsFromGMT: bundle?.utcOffsetSeconds ?? 0)
        return f.string(from: date)
    }

    // MARK: Notifications

    func syncNotifications() async {
        guard notifyOn else { Notifications.cancelAll(); return }
        guard await Notifications.requestAuthIfNeeded() else { return }

        var body = t("notif_fallback")
        if let b = bundle {
            let day = b.daily.first
            let cond = label(for: Condition.from(code: day?.code ?? b.current.code, isDay: true))
            let hi = tempStr(day?.tMax ?? b.current.temp)
            let lo = tempStr(day?.tMin ?? b.current.temp)
            let pop = Int((day?.pop ?? 0).rounded())
            body = tf("notif_body", ["cond": cond, "hi": hi, "lo": lo, "p": "\(pop)"])
        }
        let comps = Calendar.current.dateComponents([.hour, .minute], from: notifyTime)
        let title = activeLocation.map { "\(t("daily_report")) (\($0.name))" } ?? t("daily_report")
        Notifications.scheduleDaily(hour: comps.hour ?? 8, minute: comps.minute ?? 0, title: title, body: body)
    }

    // MARK: Persistence

    private func persistPrefs() {
        d.set(tempUnit.rawValue, forKey: "cw_temp")
        d.set(windUnit.rawValue, forKey: "cw_wind")
        d.set(pressUnit.rawValue, forKey: "cw_press")
        d.set(lang, forKey: "cw_lang")
        d.set(notifyOn, forKey: "cw_notify")
        let comps = Calendar.current.dateComponents([.hour, .minute], from: notifyTime)
        d.set(comps.hour ?? 8, forKey: "cw_nh")
        d.set(comps.minute ?? 0, forKey: "cw_nm")
    }

    private func saveLocations() {
        if let data = try? JSONEncoder().encode(locations) {
            d.set(data, forKey: "cw_locs")
        }
        d.set(activeID?.uuidString, forKey: "cw_active")
    }

    private func loadLocations() {
        if let data = d.data(forKey: "cw_locs"),
           let decoded = try? JSONDecoder().decode([SavedLocation].self, from: data) {
            locations = decoded
        }
        if let s = d.string(forKey: "cw_active") { activeID = UUID(uuidString: s) }
        if activeID == nil { activeID = locations.first?.id }
    }
}
