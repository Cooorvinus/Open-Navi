# Open Navi (Android 15–17)

**Open Navi** to natywna aplikacja nawigacyjno-turystyczna dla systemu **Android 15, 16 i 17 (API 35–37)** inspirowana interfejsem oraz możliwościami **Locus Map**. Zbudowana w oparciu o w 100% darmowe i otwarte technologie (**MapLibre Native**, **OpenStreetMap**, **OpenFreeMap**, **OpenTopoMap**, **CyclOSM**, **OSRM Routing**).

Gotowy plik instalacyjny znajduje się bezpośrednio w repozytorium: **[`APK/OpenNavi.apk`](APK/OpenNavi.apk)**.

---

## Kluczowe Funkcje

### 1. Mapa Wektorowa i Topo, Pozycja GPS oraz Kompas (Styl Locus Map)
- **Automatyczne wyśrodkowanie i szczegółowy Zoom na starcie**: Po uruchomieniu aplikacja domyślnie otwiera widok w przybliżeniu ulicznym (`Zoom: 16.3`) i automatycznie centruje mapę na bieżącej pozycji użytkownika z zachowaniem płynnego szczypania (pinch-to-zoom).
- **Rozwijany Górny Pasek Telemetryczny GNSS**:
  - Wyświetla na żywo liczbę satelitów (`używane/widoczne`), dokładność GPS (`±m`), wysokość n.p.m., aktualną prędkość (`km/h`), współrzędne geograficzne (`N/S`, `E/W` aktualizowane w czasie rzeczywistym wraz z poprawą pozycji) oraz aktualny poziom powiększenia (`Zoom`).
  - Po kliknięciu w pasek rozwija się zawsze na wierzchu (**warstwa priorytetowa UI**) szczegółowy **Panel Diagnostyki GNSS**: siła sygnału poszczególnych satelitów (`dBHz`), systemy satelitarne (`GPS`, `GLONASS`, `Galileo`, `BeiDou`, `QZSS`), status poprawek wysokościowych oraz aktywne źródło lokalizacji (`GPS` / `FUSED` / `NETWORK`).
- **Precyzyjna Wysokość n.p.m. (EGM2008 + Barometr)**:
  - Automatyczna konwersja wysokości elipsoidalnej WGS84 na wysokość nad poziomem morza (**MSL – geoida EGM2008**) za pomocą `AltitudeConverter` (Android API 34+) oraz wbudowanej siatki geoidy dla Europy Środkowej.
  - Fuzja z wbudowanym **czujnikiem ciśnienia atmosferycznego (barometrem `TYPE_PRESSURE`)** kalibrowanym względem wiarygodnego fixa GPS, zapewniająca stabilny odczyt wysokości nawet wewnątrz budynków.
- **Inteligentny Filtr Prędkości GPS**:
  - Eliminacja pozornego pływania prędkości w budynkach (odrzucanie skoków lokalizacji sieciowej Wi-Fi/GSM, weryfikacja prawdziwego fixa satelitarnego, niepewności pomiaru `speedAccuracyMetersPerSecond` oraz progu martwego szumu `< 1.2 km/h`).
- **Centralny Celownik (Crosshair)** z dynamicznym pomiarem odległości od aktualnej pozycji GPS.
- **Interaktywna Róża Kompasu** (fuzja czujników `TYPE_ROTATION_VECTOR` + GNSS) oraz 3 tryby pracy przycisku kompasu:
  - *Północ u góry* (reset orientacji mapy do północy),
  - *Obrót mapy według kompasu magnetycznego* (na postoju),
  - *Obrót mapy według kierunku ruchu GPS* (podczas marszu/jazdy).
- **6 Darmowych Warstw Mapowych OpenStreetMap**:
  - **OSM Liberty 3D** (wektorowa mapa z budynkami 3D i rzeźbą),
  - **OSM Bright** (czytelna mapa drogowo-miejska),
  - **OpenTopoMap** (klasyczna mapa topograficzna z poziomicami i szlakami),
  - **CyclOSM** (specjalistyczna mapa rowerowa i turystyczna),
  - **OSM Standard** (oficjalna warstwa Mapnik OpenStreetMap),
  - **OSM Positron** (jasny podkład o wysokim kontraście pod ślady GPX).

### 2. Menedżer Map Offline dla Państw (Pobieranie, Wyszukiwarka i Aktualizacje)
- **Katalog 24 Państw Europejskich** (m.in. Polska, Czechy, Słowacja, Niemcy, Austria, Włochy, Chorwacja, Szwajcaria, Hiszpania, Francja, Wielka Brytania, Norwegia, Szwecja, Finlandia, Litwa, Łotwa, Estonia, Węgry, Rumunia, Bułgaria, Grecja, Słowenia, Czarnogóra, Albania).
- **Wyszukiwarka Państw i Filtry**: Błyskawiczne filtrowanie po nazwie kraju, kodzie ISO lub słowach kluczowych (np. Tatry, Alpy, Adriatyk) oraz przełącznik *Wszystkie / Pobrane*.
- **Sprawdzanie Aktualizacji OSM**: Kontrola nagłówków `ETag` / `Last-Modified` na serwerze OpenStreetMap; przycisk aktualizacji pojawia się dla pobranych map, gdy dostępna jest nowsza wersja danych.

### 3. Rozbudowany Menedżer Punktów POI i Wyznaczania Tras (Styl Locus Map)
- **3-zakładkowy Panel Punktów i Tras**:
  - **Kreator / Edytor POI**: Wybór lub tworzenie własnych **folderów** (*Ulubione*, *Góry i Szczyty*, *Kempingi i Biwaki*, *Wyprawa*), **10 kategorii ikon** (*Szczyt górski*, *Schronisko / Nocleg*, *Punkt widokowy*, *Źródło wody*, *Kemping / Biwak*, *Parking*, *Sklep / Zaopatrzenie*, *Restauracja / Bar*, *Zagrożenie / Uwaga*, *Własny punkt*), **8 kolorów znaczników**, wybór źródła współrzędnych (**Celownik mapy**, **Aktualna pozycja GPS**, **Ręczne wpisanie Lat/Lon/Ele**) oraz pola kontaktowe (opis, telefon, adres URL).
  - **Baza Punktów POI**: Wyszukiwarka zapisanych punktów, filtrowanie po folderach, podgląd odległości w linii prostej od użytkownika, centrowanie na mapie, edycja oraz nawigacja bezpośrednio do wybranego punktu.
  - **Wyznaczanie Tras (OSRM Routing)**: Kalkulacja trasy po drogach i szlakach OpenStreetMap od pozycji GPS do celownika lub wybranego punktu POI w profilach: **Pieszy / Górski**, **Rowerowy** oraz **Samochodowy** (z automatycznym rysowaniem linii trasy na mapie).

### 4. Nagrywanie Śladów GPX i Statystyki na Żywo
- Usługa pierwszoplanowa `TrackRecordingService` zgodna z wymaganiami Androida 15–17 (`foregroundServiceType="location"`).
- Obliczanie dystansu, czasu w ruchu, średniej prędkości, sumy podejść i zejść (przewyższeń) oraz rysowanie wykresu profilu wysokościowego na żywo.
- Pełny import i eksport standardowych plików **GPX 1.1** (`GpxSerializer`).

---

## Stos Technologiczny

- **Język**: Kotlin 2.1 (JVM Target 17)
- **UI**: Jetpack Compose + Material 3 + Edge-to-Edge (`enableEdgeToEdge()`)
- **Silnik mapowy**: `org.maplibre.gl:android-sdk:11.8.0`
- **Architektura**: MVVM / Clean Architecture + Hilt DI + Kotlin Coroutines & StateFlow
- **Baza danych**: Room SQLite v2 (`OpenNaviDatabase` z obsługą śladów GPX i rozszerzonych punktów POI)

---

## Budowanie APK i Uruchomienie

### Szybkie budowanie pakietu `APK/OpenNavi.apk` (PowerShell)
W głównym katalogu projektu uruchom skrypt:
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```
Skrypt automatycznie skonfiguruje środowisko, podpisze aplikację kluczem wydania i wygeneruje gotowy plik `APK/OpenNavi.apk`.

### Uruchomienie w Android Studio
1. Otwórz projekt w **Android Studio Ladybug / Meerkat (lub nowszym)** z zainstalowanym **Android SDK 35+**.
2. Poczekaj na synchronizację Gradle (`libs.versions.toml`).
3. Uruchom aplikację na urządzeniu fizycznym lub emulatorze z Androidem 15, 16 lub 17.
