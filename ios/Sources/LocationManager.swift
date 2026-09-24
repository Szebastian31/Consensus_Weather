import Foundation
import CoreLocation

/// Thin CoreLocation wrapper exposing a one-shot async coordinate lookup.
final class LocationManager: NSObject, CLLocationManagerDelegate {
    private let mgr = CLLocationManager()
    private var cont: CheckedContinuation<CLLocationCoordinate2D?, Never>?

    override init() {
        super.init()
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func currentCoordinate() async -> CLLocationCoordinate2D? {
        await withCheckedContinuation { c in
            self.cont = c
            switch mgr.authorizationStatus {
            case .notDetermined:
                mgr.requestWhenInUseAuthorization()
            case .denied, .restricted:
                self.finish(nil)
            default:
                mgr.requestLocation()
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        switch m.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            m.requestLocation()
        case .denied, .restricted:
            finish(nil)
        default:
            break
        }
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        finish(locs.last?.coordinate)
    }

    func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        finish(nil)
    }

    private func finish(_ coord: CLLocationCoordinate2D?) {
        cont?.resume(returning: coord)
        cont = nil
    }
}
