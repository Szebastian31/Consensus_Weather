import Foundation

struct GeoResult: Identifiable, Decodable {
    let id: Int
    let name: String
    let latitude: Double
    let longitude: Double
    let country: String?
    let admin1: String?

    var subtitle: String {
        [admin1, country].compactMap { $0 }.joined(separator: ", ")
    }
}

enum OpenMeteoError: Error { case badURL, badResponse, noData }

enum OpenMeteo {
    /// The same 13 models the app UI averages.
    static let models = [
        "ecmwf_ifs025", "icon_seamless", "gfs_seamless", "metno_seamless",
        "meteofrance_seamless", "jma_seamless", "gem_seamless", "ukmo_seamless",
        "knmi_seamless", "dmi_seamless", "kma_seamless", "cma_grapes_global",
        "bom_access_global"
    ]

    // MARK: Forecast

    static func fetch(lat: Double, lon: Double) async throws -> WeatherBundle {
        var comps = URLComponents(string: "https://api.open-meteo.com/v1/forecast")!
        comps.queryItems = [
            .init(name: "latitude", value: String(lat)),
            .init(name: "longitude", value: String(lon)),
            .init(name: "models", value: models.joined(separator: ",")),
            .init(name: "hourly", value: "temperature_2m,weather_code,precipitation_probability,wind_speed_10m,wind_direction_10m,relative_humidity_2m,surface_pressure,apparent_temperature,is_day"),
            .init(name: "daily", value: "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,wind_speed_10m_max"),
            .init(name: "forecast_days", value: "7"),
            .init(name: "timeformat", value: "unixtime"),
            .init(name: "timezone", value: "auto")
        ]
        guard let url = comps.url else { throw OpenMeteoError.badURL }
        let (data, resp) = try await URLSession.shared.data(from: url)
        guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else { throw OpenMeteoError.badResponse }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw OpenMeteoError.noData }

        let utcOffset = Int(numeric(root["utc_offset_seconds"] ?? 0) ?? 0)

        guard let hourly = root["hourly"] as? [String: Any],
              let htimesRaw = hourly["time"] as? [Any] else { throw OpenMeteoError.noData }
        let hEpochs = htimesRaw.compactMap { numeric($0) }
        guard !hEpochs.isEmpty else { throw OpenMeteoError.noData }

        // Current hour = first hour whose end is still in the future.
        let now = Date().timeIntervalSince1970
        var idx0 = 0
        for (i, e) in hEpochs.enumerated() where e + 3600 > now { idx0 = i; break }

        let sources = hourly.keys.filter { $0.hasPrefix("temperature_2m_") }.count

        let current = CurrentConditions(
            temp: avg(hourly, "temperature_2m", idx0) ?? 0,
            apparent: avg(hourly, "apparent_temperature", idx0),
            code: modeCode(hourly, idx0),
            wind: avg(hourly, "wind_speed_10m", idx0) ?? 0,
            windDir: Int((avg(hourly, "wind_direction_10m", idx0) ?? 0).rounded()),
            humidity: avg(hourly, "relative_humidity_2m", idx0),
            pressure: avg(hourly, "surface_pressure", idx0),
            isDay: (avg(hourly, "is_day", idx0) ?? 1) >= 0.5
        )

        var hours: [HourPoint] = []
        let hEnd = min(idx0 + 24, hEpochs.count)
        for i in idx0..<hEnd {
            hours.append(HourPoint(
                date: Date(timeIntervalSince1970: hEpochs[i]),
                temp: avg(hourly, "temperature_2m", i) ?? 0,
                code: modeCode(hourly, i),
                pop: avg(hourly, "precipitation_probability", i) ?? 0,
                wind: avg(hourly, "wind_speed_10m", i) ?? 0,
                isDay: (avg(hourly, "is_day", i) ?? 1) >= 0.5
            ))
        }

        var days: [DayPoint] = []
        if let daily = root["daily"] as? [String: Any],
           let dtimesRaw = daily["time"] as? [Any] {
            let dEpochs = dtimesRaw.compactMap { numeric($0) }
            for i in 0..<dEpochs.count {
                days.append(DayPoint(
                    date: Date(timeIntervalSince1970: dEpochs[i]),
                    tMax: avg(daily, "temperature_2m_max", i) ?? 0,
                    tMin: avg(daily, "temperature_2m_min", i) ?? 0,
                    code: modeCode(daily, i),
                    pop: avg(daily, "precipitation_probability_max", i) ?? 0,
                    windMax: avg(daily, "wind_speed_10m_max", i) ?? 0
                ))
            }
        }

        return WeatherBundle(current: current, hourly: hours, daily: days,
                             sources: sources, updated: Date(), utcOffsetSeconds: utcOffset)
    }

    // MARK: Geocoding

    static func geocode(_ query: String, lang: String) async throws -> [GeoResult] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return [] }
        var comps = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search")!
        comps.queryItems = [
            .init(name: "name", value: q),
            .init(name: "count", value: "10"),
            .init(name: "language", value: lang),
            .init(name: "format", value: "json")
        ]
        guard let url = comps.url else { return [] }
        let (data, _) = try await URLSession.shared.data(from: url)
        struct Response: Decodable { let results: [GeoResult]? }
        return (try JSONDecoder().decode(Response.self, from: data)).results ?? []
    }

    static func reverse(lat: Double, lon: Double, lang: String) async throws -> String {
        var comps = URLComponents(string: "https://api.bigdatacloud.net/data/reverse-geocode-client")!
        comps.queryItems = [
            .init(name: "latitude", value: String(lat)),
            .init(name: "longitude", value: String(lon)),
            .init(name: "localityLanguage", value: lang)
        ]
        guard let url = comps.url else { return "" }
        let (data, _) = try await URLSession.shared.data(from: url)
        let j = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let city = j?["city"] as? String, !city.isEmpty { return city }
        if let loc = j?["locality"] as? String, !loc.isEmpty { return loc }
        return (j?["principalSubdivision"] as? String) ?? ""
    }

    // MARK: Consensus helpers

    private static func numeric(_ v: Any) -> Double? {
        if let d = v as? Double { return d }
        if let i = v as? Int { return Double(i) }
        if let n = v as? NSNumber { return n.doubleValue }
        return nil
    }

    /// Average a value across every model-suffixed array at `idx`.
    private static func avg(_ dict: [String: Any], _ base: String, _ idx: Int) -> Double? {
        var sum = 0.0, n = 0
        for (k, v) in dict where k == base || k.hasPrefix(base + "_") {
            guard let arr = v as? [Any], idx >= 0, idx < arr.count,
                  !(arr[idx] is NSNull), let d = numeric(arr[idx]) else { continue }
            sum += d; n += 1
        }
        return n > 0 ? sum / Double(n) : nil
    }

    /// Most frequent weather code across models at `idx` (ties break to the higher/more-significant code).
    private static func modeCode(_ dict: [String: Any], _ idx: Int) -> Int {
        var counts: [Int: Int] = [:]
        for (k, v) in dict where k == "weather_code" || k.hasPrefix("weather_code_") {
            guard let arr = v as? [Any], idx >= 0, idx < arr.count,
                  !(arr[idx] is NSNull), let d = numeric(arr[idx]) else { continue }
            counts[Int(d), default: 0] += 1
        }
        return counts.sorted {
            $0.value != $1.value ? $0.value > $1.value : $0.key > $1.key
        }.first?.key ?? 0
    }
}
