# OpenMesh

**Czat offline przez Bluetooth i Wi‑Fi Direct – bez internetu, bez serwerów, bez kont.** Telefony łączą się bezpośrednio, a wiadomości przeskakują od urządzenia do urządzenia. Przydaje się w górach, na festiwalach, w samolocie i podczas awarii sieci.

[![Build & Release](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

## Pobieranie

Najnowsza wersja jest zawsze w **[Releases → Latest](../../releases/latest)**. Każdy wypchnięty tag `vX.Y.Z` buduje i publikuje nowe wydanie, a aplikacja sama się aktualizuje, gdy telefon ma internet.

1. Pobierz `OpenMesh-x.y.z.apk` na telefon.
2. Otwórz plik i zezwól na instalację z tego źródła.
3. Wymagany Android 8.0 (API 26) lub nowszy oraz Usługi Google Play (Nearby Connections).

## Funkcje

### Sieć
| Funkcja | Opis |
|---|---|
| **Automatyczne łączenie** | Telefony z OpenMesh wykrywają się i łączą same – Bluetooth, BLE, Wi‑Fi Direct / hotspot (medium dobiera Nearby Connections). |
| **Mesh** | Każdy telefon przekazuje wiadomości dalej (do 10 skoków); duplikaty są odrzucane. |
| **Store-and-forward** | Bez zasięgu wiadomość czeka w kolejce. Ostatnie pakiety (domyślnie 48 h) trafiają do każdego nowego sąsiada – osoba przechodząca między grupami zanosi wiadomości dalej. |
| **Układ sieci** | Klaster (każdy z każdym – grupy w ruchu) albo gwiazda (jeden telefon jako centrum, szybsze Wi‑Fi Direct). |
| **Praca w tle** | Usługa pierwszoplanowa utrzymuje połączenia przy wygaszonym ekranie; start po uruchomieniu telefonu. |
| **Tryb samolotowy** | Wystarczy ponownie włączyć Bluetooth i Wi‑Fi. |

### Rozmowy
| Funkcja | Opis |
|---|---|
| **#ogólny** | Kanał publiczny – widzą go wszyscy w zasięgu sieci. |
| **Własne kanały** | Jawne albo chronione hasłem (treść szyfrowana). Lista kanałów widzianych w pobliżu, do których można dołączyć. |
| **Wiadomości prywatne** | Szyfrowane end-to-end, z potwierdzeniem doręczenia ✓✓. |
| **Szybkie odpowiedzi** | „Jestem OK”, „Gdzie jesteś?”, „Idę do was”… jednym dotknięciem. |
| **Stany wiadomości** | W kolejce · wysłano do sieci · doręczono; liczba węzłów, przez które przeszła wiadomość. |
| **Szukanie** | Jedno pole przeszukuje całą historię: wiadomości, kanały i osoby – także te z archiwum. |
| **Przypinanie i archiwum** | Ważne rozmowy na górze listy, resztę można schować. Przytrzymanie rozmowy otwiera jej opcje. |
| **Eksport** | Zapis rozmowy albo całej historii do pliku tekstowego (czas, autor, pozycje) przez „Udostępnij”. |
| **Zdjęcia i pliki** | Załącznik jedzie w częściach po 12 kB jako zwykłe pakiety mesh – z przekazywaniem, store-and-forward i szyfrowaniem. Zdjęcia są zmniejszane do 1280 px, złożony plik jest sprawdzany skrótem SHA-256, a brakujące części aplikacja doprasza sama. |

### Bezpieczeństwo w terenie
| Funkcja | Opis |
|---|---|
| **SOS** | Wezwanie z pozycją GPS do wszystkich w sieci, z maksymalnym zasięgiem i alarmem dźwiękowym. Wysyłane przytrzymaniem przycisku – bez przypadkowych alarmów. |
| **Pozycja** | Udostępnianie współrzędnych (GPS działa offline), odległość i kierunek do innych, zapis w stopniach/minutach/sekundach, otwieranie w mapach offline (OsmAnd, Organic Maps). |
| **W pobliżu** | Radar sieci, osoby bezpośrednio i przez innych, status, poziom baterii, odległość. |
| **Mapa okolicy** | Osoby z pozycją naniesione względem ciebie: odległość, kierunek, skala, kierunki świata. Rysowana z samych współrzędnych – bez kafelków i bez internetu. |
| **Latarka alarmowa** | Miganie diodą i ekranem: SOS alfabetem Morse'a, sygnał alpejski (6 mignięć na minutę), szybkie mignięcia albo światło ciągłe. |

### Prywatność i kryptografia
- Tożsamość: klucz **P-256** generowany na telefonie; identyfikator = skrót klucza publicznego.
- Każdy pakiet podpisany **ECDSA** – nie da się podszyć pod nadawcę ani zmienić treści po drodze.
- Rozmowy prywatne: **ECDH + AES-256-GCM**. Kanały z hasłem: **PBKDF2-HMAC-SHA256 + AES-256-GCM**.
- Kanały jawne i SOS są czytelne dla wszystkich – celowo.
- Załączniki idą tym samym kanałem co tekst: część po części, każda podpisana i (w rozmowach prywatnych oraz kanałach z hasłem) zaszyfrowana. Plik zapisuje się dopiero, gdy skrót SHA-256 całości się zgadza.
- Odcisk klucza do porównania na obu telefonach, blokowanie osób, nowa tożsamość, **tryb paniki** (natychmiastowe usunięcie wszystkich danych).
- Brak serwerów, kont, numerów telefonu i analityki. Internet jest używany wyłącznie do sprawdzania aktualizacji.

## Zdjęcia i pliki w praktyce

Jedna część to 12 kB, między częściami jest krótka przerwa, żeby zwykłe wiadomości dalej przechodziły. Zdjęcie o rozmiarze 300 kB to kilkadziesiąt części i zwykle kilkanaście sekund przy bezpośrednim połączeniu; 3 MB przez kilka skoków to już kilka minut. Części, które nie doszły, aplikacja doprasza u nadawcy – wystarczy, że oba telefony znajdą się kiedykolwiek w jednej sieci.

## Zasięg

Zwykle 10–100 m między dwoma telefonami (więcej w otwartym terenie, mniej w budynkach i w tłumie). Każdy kolejny telefon z OpenMesh wydłuża sieć.

## Budowanie

```bash
gradle :app:assembleDebug
```

Wymagany JDK 17 i Android SDK 36. Wersja wydania pochodzi z pliku `VERSION`; workflow `.github/workflows/android.yml` uruchamia testy, buduje podpisany APK i publikuje wydanie na GitHubie.

Podpis wydań: sekrety repozytorium `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` (PKCS12). Ten sam klucz jest potrzebny, by kolejne wersje instalowały się jako aktualizacja.

## Licencja

MIT
