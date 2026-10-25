# Open Navi (Android 15–17)

**Open Navi** to natywna aplikacja nawigacyjno-turystyczna dla systemu **Android 15, 16 i 17 (API 35–37)** inspirowana interfejsem oraz możliwościami **Locus Map**. Zbudowana w oparciu o w 100% darmowe i otwarte technologie (**MapLibre Native**, **OpenStreetMap**, **PMTiles/Offline Regions**, **OSRM Routing**).

---

## Kluczowe Funkcje

1. **Mapa Wektorowa, Pozycja GPS i Kompas (Styl Locus Map)**:
   - Górny pasek telemetryczny GNSS (liczba widocznych i używanych satelitów, dokładność GPS, wysokość n.p.m., aktualna prędkość, współrzędne geograficzne).
   - Centralny celownik (Crosshair) z dynamicznym pomiarem odległości od aktualnej pozycji GPS.
   - Interaktywna róża kompasu (fuzja czujników `TYPE_ROTATION_VECTOR` + GNSS) oraz 3 tryby orientacji mapy: *Północ u góry*, *Obrót według kompasu*, *Obrót według kierunku ruchu GPS*.
   - Widok 3D z pochyleniem terenu (Tilt) oraz przełączanie darmowych stylów wektorowych OpenStreetMap (*OSM Liberty Topo*, *OSM Bright*, *OSM Positron*).

2. **Menedżer Map Offline dla Państw (Pobieranie i Aktualizacja)**:
   - Katalog państw (Polska, Czechy, Słowacja, Niemcy, Austria, Włochy, Chorwacja, Szwajcaria, Hiszpania, Norwegia, Litwa).
   - Lokalny zapis paczek map oraz regionów offline MapLibre w pamięci urządzenia.
   - Sprawdzanie dostępności nowszych wersji map OpenStreetMap na serwerze (`ETag` / `Last-Modified`) i jednoklikowa aktualizacja wybranego państwa.

3. **Nagrywanie Śladów GPX i Statystyki na Żywo**:
   - Usługa pierwszoplanowa `TrackRecordingService` zgodna z wymaganiami Androida 15–17 (`foregroundServiceType="location"`).
   - Obliczanie dystansu, czasu w ruchu, średniej prędkości, sumy podejść i zejść (przewyższeń) oraz rysowanie wykresu profilu wysokościowego na żywo.
   - Pełny import i eksport standardowych plików **GPX 1.1** (`GpxSerializer`).

4. **Punkty (Waypoints / POI) oraz Wyznaczanie Tras (Routing)**:
   - Dodawanie własnych punktów POI (Szczyt, Schronisko, Parking, Źródło wody, Ostrzeżenie) w miejscu celownika lub pozycji GPS.
   - Wyznaczanie tras po danych OpenStreetMap dla profili: **Pieszy / Górski**, **Rowerowy**, **Samochodowy**.

---

## Stos Technologiczny

- **Język**: Kotlin 2.1 (JVM Target 21)
- **UI**: Jetpack Compose + Material 3 + Edge-to-Edge (`enableEdgeToEdge()`)
- **Silnik mapowy**: `org.maplibre.gl:android-sdk:11.8.0`
- **Architektura**: MVVM / Clean Architecture + Hilt DI + Kotlin Coroutines & StateFlow
- **Baza danych**: Room SQLite (`OpenNaviDatabase`)

---

## Uruchomienie w Android Studio

1. Otwórz projekt w **Android Studio Ladybug / Meerkat (lub nowszym)** z zainstalowanym **Android SDK 35 / 36**.
2. Poczekaj na synchronizację Gradle (`libs.versions.toml`).
3. Uruchom aplikację na urządzeniu fizycznym lub emulatorze z Androidem 15, 16 lub 17.
