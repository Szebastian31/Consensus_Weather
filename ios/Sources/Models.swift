import SwiftUI

// MARK: - Weather condition

enum Condition: String {
    case clearDay, clearNight, pcloudy, cloudy, overcast, fog, rain, snow, thunder

    /// Map a WMO weather code to a condition (mirrors the web/Android app).
    static func from(code: Int, isDay: Bool) -> Condition {
        switch code {
        case 0:            return isDay ? .clearDay : .clearNight
        case 1, 2:         return .pcloudy
        case 3:            return .overcast
        case 45, 48:       return .fog
        case 71...77, 85, 86: return .snow
        case let c where c >= 95: return .thunder
        case 51...67, 80...82:    return .rain
        default:           return .cloudy
        }
    }

    /// Localization key for the human label.
    var labelKey: String {
        switch self {
        case .clearDay:   return "cond_clear_day"
        case .clearNight: return "cond_clear_night"
        case .pcloudy:    return "cond_pcloudy"
        case .cloudy:     return "cond_cloudy"
        case .overcast:   return "cond_overcast"
        case .fog:        return "cond_fog"
        case .rain:       return "cond_rain"
        case .snow:       return "cond_snow"
        case .thunder:    return "cond_thunder"
        }
    }

    func symbol(isDay: Bool) -> String {
        switch self {
        case .clearDay:   return "sun.max.fill"
        case .clearNight: return "moon.stars.fill"
        case .pcloudy:    return isDay ? "cloud.sun.fill" : "cloud.moon.fill"
        case .cloudy:     return "cloud.fill"
        case .overcast:   return "smoke.fill"
        case .fog:        return "cloud.fog.fill"
        case .rain:       return "cloud.rain.fill"
        case .snow:       return "cloud.snow.fill"
        case .thunder:    return "cloud.bolt.rain.fill"
        }
    }

    /// Living-sky background gradient.
    func gradient(isDay: Bool) -> [Color] {
        switch self {
        case .clearDay:   return [Color(hex: 0x2E7CF6), Color(hex: 0x8FC0FF)]
        case .clearNight: return [Color(hex: 0x0B1E3A), Color(hex: 0x24406E)]
        case .pcloudy:    return isDay ? [Color(hex: 0x4B84D8), Color(hex: 0xAFC7E6)]
                                       : [Color(hex: 0x141E33), Color(hex: 0x37507A)]
        case .cloudy, .overcast: return [Color(hex: 0x51617A), Color(hex: 0x8895A8)]
        case .fog:        return [Color(hex: 0x6B7688), Color(hex: 0xAEB6C2)]
        case .rain:       return [Color(hex: 0x2C3E55), Color(hex: 0x557089)]
        case .snow:       return [Color(hex: 0x5E6E86), Color(hex: 0xC3D2E4)]
        case .thunder:    return [Color(hex: 0x1B1F2E), Color(hex: 0x3E3357)]
        }
    }

    /// Simple particle overlay mode for the forecast background.
    var precipMode: PrecipMode {
        switch self {
        case .rain, .thunder: return .rain
        case .snow:           return .snow
        default:              return .none
        }
    }
}

enum PrecipMode { case none, rain, snow }

// MARK: - Forecast data

struct HourPoint: Identifiable {
    let id = UUID()
    let date: Date
    let temp: Double
    let code: Int
    let pop: Double        // precipitation probability, %
    let wind: Double       // km/h
    let isDay: Bool
}

struct DayPoint: Identifiable {
    let id = UUID()
    let date: Date
    let tMax: Double
    let tMin: Double
    let code: Int
    let pop: Double
    let windMax: Double
}

struct CurrentConditions {
    let temp: Double
    let apparent: Double?
    let code: Int
    let wind: Double
    let windDir: Int
    let humidity: Double?
    let pressure: Double?   // hPa
    let isDay: Bool
    var condition: Condition { Condition.from(code: code, isDay: isDay) }
}

struct WeatherBundle {
    let current: CurrentConditions
    let hourly: [HourPoint]
    let daily: [DayPoint]
    let sources: Int
    let updated: Date
    let utcOffsetSeconds: Int
}

// MARK: - Saved locations

struct SavedLocation: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    var lat: Double
    var lon: Double
    var isCurrent: Bool = false
}

// MARK: - Color helper

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}
