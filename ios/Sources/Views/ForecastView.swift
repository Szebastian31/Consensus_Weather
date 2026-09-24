import SwiftUI

struct ForecastView: View {
    @EnvironmentObject var store: Store

    var body: some View {
        ZStack {
            background
            Group {
                if store.loading && store.bundle == nil {
                    ProgressView().tint(.white)
                } else if let b = store.bundle {
                    content(b)
                } else {
                    emptyState
                }
            }
        }
        .refreshable { await store.refresh() }
    }

    private var currentCondition: Condition {
        if let b = store.bundle {
            return Condition.from(code: b.current.code, isDay: b.current.isDay)
        }
        return .clearDay
    }

    private var background: some View {
        let isDay = store.bundle?.current.isDay ?? true
        return ZStack {
            LinearGradient(colors: currentCondition.gradient(isDay: isDay),
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            PrecipOverlay(mode: currentCondition.precipMode)
                .ignoresSafeArea()
        }
    }

    @ViewBuilder private func content(_ b: WeatherBundle) -> some View {
        ScrollView {
            VStack(spacing: 22) {
                hero(b)
                statRow(b)
                hourly(b)
                daily(b)
                footer(b)
            }
            .padding()
            .padding(.bottom, 40)
        }
    }

    // MARK: Sections

    private func hero(_ b: WeatherBundle) -> some View {
        let cond = Condition.from(code: b.current.code, isDay: b.current.isDay)
        return VStack(spacing: 6) {
            Image(systemName: cond.symbol(isDay: b.current.isDay))
                .symbolRenderingMode(.multicolor)
                .font(.system(size: 72))
                .shadow(radius: 8)
                .padding(.bottom, 4)
            Text(store.tempStr(b.current.temp))
                .font(.system(size: 72, weight: .thin))
            Text(store.label(for: cond))
                .font(.title3.weight(.medium))
            if let ap = b.current.apparent {
                Text("\(store.t("feels_like")) \(store.tempStr(ap))")
                    .font(.subheadline).opacity(0.85)
            }
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
    }

    private func statRow(_ b: WeatherBundle) -> some View {
        HStack(spacing: 12) {
            if let h = b.current.humidity {
                stat("humidity.fill", store.t("humidity"), "\(Int(h.rounded()))%")
            }
            stat("wind", store.t("wind"), "\(store.windStr(b.current.wind)) \(store.windUnit.label)")
            if let p = b.current.pressure {
                stat("gauge.medium", store.t("pressure"), "\(store.pressStr(p)) \(store.pressUnit.label)")
            }
        }
    }

    private func stat(_ icon: String, _ title: String, _ value: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon).font(.title3)
            Text(value).font(.callout.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.7)
            Text(title).font(.caption2).opacity(0.8)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.14)))
    }

    private func hourly(_ b: WeatherBundle) -> some View {
        card(store.t("hourly_forecast")) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 18) {
                    ForEach(b.hourly) { h in
                        let c = Condition.from(code: h.code, isDay: h.isDay)
                        VStack(spacing: 8) {
                            Text(store.hourLabel(h.date)).font(.caption)
                            Image(systemName: c.symbol(isDay: h.isDay))
                                .symbolRenderingMode(.multicolor).font(.title3)
                            Text(store.tempStr(h.temp)).font(.callout.weight(.semibold))
                            Text(h.pop >= 15 ? "\(Int(h.pop.rounded()))%" : " ")
                                .font(.caption2).foregroundStyle(Color(hex: 0x9FD3FF))
                        }
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    private func daily(_ b: WeatherBundle) -> some View {
        card(store.t("daily_forecast")) {
            VStack(spacing: 12) {
                ForEach(b.daily) { d in
                    let c = Condition.from(code: d.code, isDay: true)
                    HStack(spacing: 10) {
                        Text(store.weekdayLabel(d.date)).frame(width: 44, alignment: .leading)
                        Image(systemName: c.symbol(isDay: true))
                            .symbolRenderingMode(.multicolor).frame(width: 26)
                        Text(d.pop >= 15 ? "\(Int(d.pop.rounded()))%" : "")
                            .font(.caption).foregroundStyle(Color(hex: 0x9FD3FF))
                            .frame(width: 38, alignment: .leading)
                        Spacer()
                        Text(store.tempStr(d.tMin)).opacity(0.7)
                        Text(store.tempStr(d.tMax)).fontWeight(.semibold)
                            .frame(minWidth: 40, alignment: .trailing)
                    }
                    .font(.callout)
                }
            }
        }
    }

    private func footer(_ b: WeatherBundle) -> some View {
        VStack(spacing: 4) {
            Text(store.tf("consensus_avg", ["n": "\(b.sources)"]).uppercased())
                .font(.caption2.weight(.semibold)).opacity(0.9)
            Text("Weather data by Open-Meteo.com")
                .font(.caption2).opacity(0.7)
        }
        .foregroundStyle(.white)
        .padding(.top, 6)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "cloud.slash").font(.system(size: 42))
            Text(store.errorText ?? store.t("fetch_error"))
                .multilineTextAlignment(.center)
            Button(store.t("use_current")) { Task { await store.useCurrentLocation() } }
                .buttonStyle(.borderedProminent)
                .tint(.white.opacity(0.25))
        }
        .foregroundStyle(.white)
        .padding()
    }

    // MARK: Card container

    @ViewBuilder private func card<C: View>(_ title: String, @ViewBuilder _ inner: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title.uppercased()).font(.caption.weight(.bold)).opacity(0.8)
            inner()
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 22).fill(.white.opacity(0.12)))
    }
}

// MARK: - Lightweight rain/snow particle overlay

struct PrecipOverlay: View {
    let mode: PrecipMode

    var body: some View {
        Group {
            if mode == .none {
                Color.clear
            } else {
                TimelineView(.animation) { timeline in
                    Canvas { ctx, size in
                        let t = timeline.date.timeIntervalSinceReferenceDate
                        let count = mode == .rain ? 90 : 60
                        for i in 0..<count {
                            let seed = Double(i)
                            let rx = abs((sin(seed * 12.9898) * 43758.5453).truncatingRemainder(dividingBy: 1))
                            let px = rx * size.width
                            let speed = mode == .rain ? 460.0 : 90.0
                            let phase = (seed * 0.137).truncatingRemainder(dividingBy: 1)
                            let py = ((t * speed / size.height + phase).truncatingRemainder(dividingBy: 1)) * size.height
                            if mode == .rain {
                                var path = Path()
                                path.move(to: CGPoint(x: px, y: py))
                                path.addLine(to: CGPoint(x: px, y: py + 12))
                                ctx.stroke(path, with: .color(.white.opacity(0.22)), lineWidth: 1)
                            } else {
                                let wob = CGFloat(sin(t + seed)) * 6
                                ctx.fill(Path(ellipseIn: CGRect(x: px + wob, y: py, width: 4, height: 4)),
                                         with: .color(.white.opacity(0.5)))
                            }
                        }
                    }
                }
            }
        }
        .allowsHitTesting(false)
    }
}
