import Foundation

/// Lightweight dictionary-based localization that mirrors the web app's `I18N` map.
/// English is the fallback; the remaining ~26 languages can be ported from the web
/// app's `I18N` object using these same keys.
enum L10n {
    static let supported = ["en", "pl", "de", "es", "fr", "it", "uk"]

    static func deviceLang() -> String {
        let code = String((Locale.preferredLanguages.first ?? "en").prefix(2)).lowercased()
        return supported.contains(code) ? code : "en"
    }

    static func t(_ lang: String, _ key: String) -> String {
        dict[lang]?[key] ?? dict["en"]?[key] ?? key
    }

    static func tf(_ lang: String, _ key: String, _ vars: [String: String]) -> String {
        var s = t(lang, key)
        for (k, v) in vars { s = s.replacingOccurrences(of: "{\(k)}", with: v) }
        return s
    }

    static let dict: [String: [String: String]] = [
        "en": [
            "feels_like": "Feels like", "humidity": "Humidity", "wind": "Wind", "pressure": "Pressure",
            "hourly_forecast": "Hourly", "daily_forecast": "7-day forecast",
            "search_placeholder": "Search city…", "add": "Add", "use_current": "Use current location",
            "my_location": "My location", "saved_locations": "Saved places", "locations": "Locations",
            "settings": "Settings", "units": "Units", "temperature": "Temperature", "wind_speed": "Wind speed",
            "pressure_label": "Pressure", "language": "Language", "notifications": "Notifications",
            "daily_notification": "Daily forecast", "notify_time": "Time", "updated_at": "Updated",
            "consensus_avg": "{n}-model average", "chance_of_rain": "{p}% chance of rain",
            "fetch_error": "Couldn't load weather. Pull to retry.", "loc_denied": "Location off — add a city instead.",
            "done": "Done", "close": "Close", "daily_report": "Daily Report",
            "notif_body": "{cond}, high {hi} / low {lo}, {p}% chance of rain.",
            "notif_fallback": "Your daily weather summary.",
            "cond_clear_day": "Sunny", "cond_clear_night": "Clear", "cond_pcloudy": "Partly cloudy",
            "cond_cloudy": "Cloudy", "cond_overcast": "Overcast", "cond_fog": "Fog",
            "cond_rain": "Rain", "cond_snow": "Snow", "cond_thunder": "Thunderstorms"
        ],
        "pl": [
            "feels_like": "Odczuwalna", "humidity": "Wilgotność", "wind": "Wiatr", "pressure": "Ciśnienie",
            "hourly_forecast": "Godzinowa", "daily_forecast": "Prognoza 7-dniowa",
            "search_placeholder": "Szukaj miasta…", "add": "Dodaj", "use_current": "Użyj obecnej lokalizacji",
            "my_location": "Moja lokalizacja", "saved_locations": "Zapisane miejsca", "locations": "Lokalizacje",
            "settings": "Ustawienia", "units": "Jednostki", "temperature": "Temperatura", "wind_speed": "Prędkość wiatru",
            "pressure_label": "Ciśnienie", "language": "Język", "notifications": "Powiadomienia",
            "daily_notification": "Prognoza dzienna", "notify_time": "Godzina", "updated_at": "Zaktualizowano",
            "consensus_avg": "średnia z {n} modeli", "chance_of_rain": "{p}% szans na deszcz",
            "fetch_error": "Nie udało się pobrać pogody. Pociągnij, aby ponowić.", "loc_denied": "Lokalizacja wyłączona — dodaj miasto.",
            "done": "Gotowe", "close": "Zamknij", "daily_report": "Raport dzienny",
            "notif_body": "{cond}, maks. {hi} / min. {lo}, {p}% szans na deszcz.",
            "notif_fallback": "Twoje dzienne podsumowanie pogody.",
            "cond_clear_day": "Słonecznie", "cond_clear_night": "Bezchmurnie", "cond_pcloudy": "Częściowe zachmurzenie",
            "cond_cloudy": "Pochmurno", "cond_overcast": "Zachmurzenie całkowite", "cond_fog": "Mgła",
            "cond_rain": "Deszcz", "cond_snow": "Śnieg", "cond_thunder": "Burze"
        ],
        "de": [
            "feels_like": "Gefühlt", "humidity": "Luftfeuchte", "wind": "Wind", "pressure": "Luftdruck",
            "hourly_forecast": "Stündlich", "daily_forecast": "7-Tage-Vorhersage",
            "search_placeholder": "Stadt suchen…", "add": "Hinzufügen", "use_current": "Aktuellen Standort verwenden",
            "my_location": "Mein Standort", "saved_locations": "Gespeicherte Orte", "locations": "Orte",
            "settings": "Einstellungen", "units": "Einheiten", "temperature": "Temperatur", "wind_speed": "Windgeschwindigkeit",
            "pressure_label": "Luftdruck", "language": "Sprache", "notifications": "Benachrichtigungen",
            "daily_notification": "Tägliche Vorhersage", "notify_time": "Uhrzeit", "updated_at": "Aktualisiert",
            "consensus_avg": "Mittel aus {n} Modellen", "chance_of_rain": "{p}% Regenwahrscheinlichkeit",
            "fetch_error": "Wetter konnte nicht geladen werden. Zum Wiederholen ziehen.", "loc_denied": "Standort aus — Stadt hinzufügen.",
            "done": "Fertig", "close": "Schließen", "daily_report": "Tagesbericht",
            "notif_body": "{cond}, Höchst {hi} / Tiefst {lo}, {p}% Regenrisiko.",
            "notif_fallback": "Deine tägliche Wetterübersicht.",
            "cond_clear_day": "Sonnig", "cond_clear_night": "Klar", "cond_pcloudy": "Teilweise bewölkt",
            "cond_cloudy": "Bewölkt", "cond_overcast": "Bedeckt", "cond_fog": "Nebel",
            "cond_rain": "Regen", "cond_snow": "Schnee", "cond_thunder": "Gewitter"
        ],
        "es": [
            "feels_like": "Sensación", "humidity": "Humedad", "wind": "Viento", "pressure": "Presión",
            "hourly_forecast": "Por hora", "daily_forecast": "Pronóstico de 7 días",
            "search_placeholder": "Buscar ciudad…", "add": "Añadir", "use_current": "Usar ubicación actual",
            "my_location": "Mi ubicación", "saved_locations": "Lugares guardados", "locations": "Ubicaciones",
            "settings": "Ajustes", "units": "Unidades", "temperature": "Temperatura", "wind_speed": "Velocidad del viento",
            "pressure_label": "Presión", "language": "Idioma", "notifications": "Notificaciones",
            "daily_notification": "Pronóstico diario", "notify_time": "Hora", "updated_at": "Actualizado",
            "consensus_avg": "media de {n} modelos", "chance_of_rain": "{p}% de probabilidad de lluvia",
            "fetch_error": "No se pudo cargar el clima. Desliza para reintentar.", "loc_denied": "Ubicación desactivada — añade una ciudad.",
            "done": "Listo", "close": "Cerrar", "daily_report": "Informe diario",
            "notif_body": "{cond}, máx. {hi} / mín. {lo}, {p}% de lluvia.",
            "notif_fallback": "Tu resumen del clima diario.",
            "cond_clear_day": "Soleado", "cond_clear_night": "Despejado", "cond_pcloudy": "Parcialmente nublado",
            "cond_cloudy": "Nublado", "cond_overcast": "Cubierto", "cond_fog": "Niebla",
            "cond_rain": "Lluvia", "cond_snow": "Nieve", "cond_thunder": "Tormentas"
        ],
        "fr": [
            "feels_like": "Ressenti", "humidity": "Humidité", "wind": "Vent", "pressure": "Pression",
            "hourly_forecast": "Par heure", "daily_forecast": "Prévisions sur 7 jours",
            "search_placeholder": "Rechercher une ville…", "add": "Ajouter", "use_current": "Utiliser ma position",
            "my_location": "Ma position", "saved_locations": "Lieux enregistrés", "locations": "Lieux",
            "settings": "Réglages", "units": "Unités", "temperature": "Température", "wind_speed": "Vitesse du vent",
            "pressure_label": "Pression", "language": "Langue", "notifications": "Notifications",
            "daily_notification": "Prévision quotidienne", "notify_time": "Heure", "updated_at": "Mis à jour",
            "consensus_avg": "moyenne de {n} modèles", "chance_of_rain": "{p}% de risque de pluie",
            "fetch_error": "Impossible de charger la météo. Tirez pour réessayer.", "loc_denied": "Localisation désactivée — ajoutez une ville.",
            "done": "Terminé", "close": "Fermer", "daily_report": "Rapport quotidien",
            "notif_body": "{cond}, max {hi} / min {lo}, {p}% de pluie.",
            "notif_fallback": "Votre résumé météo quotidien.",
            "cond_clear_day": "Ensoleillé", "cond_clear_night": "Dégagé", "cond_pcloudy": "Partiellement nuageux",
            "cond_cloudy": "Nuageux", "cond_overcast": "Couvert", "cond_fog": "Brouillard",
            "cond_rain": "Pluie", "cond_snow": "Neige", "cond_thunder": "Orages"
        ],
        "it": [
            "feels_like": "Percepita", "humidity": "Umidità", "wind": "Vento", "pressure": "Pressione",
            "hourly_forecast": "Orario", "daily_forecast": "Previsioni a 7 giorni",
            "search_placeholder": "Cerca città…", "add": "Aggiungi", "use_current": "Usa posizione attuale",
            "my_location": "La mia posizione", "saved_locations": "Luoghi salvati", "locations": "Luoghi",
            "settings": "Impostazioni", "units": "Unità", "temperature": "Temperatura", "wind_speed": "Velocità del vento",
            "pressure_label": "Pressione", "language": "Lingua", "notifications": "Notifiche",
            "daily_notification": "Previsione giornaliera", "notify_time": "Ora", "updated_at": "Aggiornato",
            "consensus_avg": "media di {n} modelli", "chance_of_rain": "{p}% di probabilità di pioggia",
            "fetch_error": "Impossibile caricare il meteo. Trascina per riprovare.", "loc_denied": "Posizione disattivata — aggiungi una città.",
            "done": "Fatto", "close": "Chiudi", "daily_report": "Report giornaliero",
            "notif_body": "{cond}, max {hi} / min {lo}, {p}% di pioggia.",
            "notif_fallback": "Il tuo riepilogo meteo giornaliero.",
            "cond_clear_day": "Soleggiato", "cond_clear_night": "Sereno", "cond_pcloudy": "Parzialmente nuvoloso",
            "cond_cloudy": "Nuvoloso", "cond_overcast": "Coperto", "cond_fog": "Nebbia",
            "cond_rain": "Pioggia", "cond_snow": "Neve", "cond_thunder": "Temporali"
        ],
        "uk": [
            "feels_like": "Відчувається", "humidity": "Вологість", "wind": "Вітер", "pressure": "Тиск",
            "hourly_forecast": "Погодинно", "daily_forecast": "Прогноз на 7 днів",
            "search_placeholder": "Пошук міста…", "add": "Додати", "use_current": "Поточне місцезнаходження",
            "my_location": "Моє місце", "saved_locations": "Збережені місця", "locations": "Місця",
            "settings": "Налаштування", "units": "Одиниці", "temperature": "Температура", "wind_speed": "Швидкість вітру",
            "pressure_label": "Тиск", "language": "Мова", "notifications": "Сповіщення",
            "daily_notification": "Щоденний прогноз", "notify_time": "Час", "updated_at": "Оновлено",
            "consensus_avg": "середнє з {n} моделей", "chance_of_rain": "{p}% ймовірність дощу",
            "fetch_error": "Не вдалося завантажити погоду. Потягніть, щоб повторити.", "loc_denied": "Геолокацію вимкнено — додайте місто.",
            "done": "Готово", "close": "Закрити", "daily_report": "Щоденний звіт",
            "notif_body": "{cond}, макс. {hi} / мін. {lo}, {p}% дощу.",
            "notif_fallback": "Ваш щоденний огляд погоди.",
            "cond_clear_day": "Сонячно", "cond_clear_night": "Ясно", "cond_pcloudy": "Мінлива хмарність",
            "cond_cloudy": "Хмарно", "cond_overcast": "Похмуро", "cond_fog": "Туман",
            "cond_rain": "Дощ", "cond_snow": "Сніг", "cond_thunder": "Грози"
        ]
    ]
}
