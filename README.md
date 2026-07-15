# ToonItalia - Scraper & Android App

Scraper Python e app Android per visualizzare i contenuti di toonitalia.xyz.

## Struttura del Progetto

```
ToonItalia/
├── scraper/
│   ├── scraper.py          # Scraper Python standalone
│   ├── api_server.py       # Server API Flask
│   └── requirements.txt    # Dipendenze Python
├── android/
│   ├── app/
│   │   ├── build.gradle
│   │   ├── proguard-rules.pro
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/toonitalia/app/
│   │       │   ├── Models.kt
│   │       │   ├── NetworkModule.kt
│   │       │   ├── Scraper.kt
│   │       │   ├── ToonItaliaApp.kt
│   │       │   ├── MainActivity.kt
│   │       │   └── PlayerActivity.kt
│   │       └── res/
│   │           └── values/
│   ├── build.gradle
│   ├── settings.gradle
│   └── gradle.properties
└── README.md
```

## Scraper Python

### Installazione

```bash
cd scraper
pip install -r requirements.txt
```

### Uso Standalone

```bash
python scraper.py
# 1 = Homepage, 2 = Tutto, 3 = Singola URL
```

### Server API

```bash
python api_server.py
# http://localhost:5000/api/homepage
# http://localhost:5000/api/list/anime-ita
# http://localhost:5000/api/detail?url=...
# http://localhost:5000/api/search?q=one+piece
```

## App Android

### Requisiti
- Android Studio Hedgehog (2023.1.1) o superiore
- SDK 34
- Kotlin 1.9.21

### Build
Apri la cartella `android/` in Android Studio e fai Run.

Compatibile con:
- **Telefoni Android** (API 21+)
- **Android TV** (leanback)
- **Chromebook** (modalita' tablet/laptop)

### Funzionalita'
- Navigazione per categorie (Anime Ita, Sub-Ita, Film, Serie TV)
- Ricerca contenuti
- Dettaglio con trama e info
- Lista episodi con player integrato
- Supporto video HLS (m3u8) e MP4
- UI dark mode ottimizzata per TV
- Player con controlli a schermo intero
