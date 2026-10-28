# Zmiany

## 0.2.0

Szukanie, porządek w rozmowach, mapa okolicy i sygnalizacja światłem.

- **Szukanie w historii**: jedno pole przeszukuje wszystkie wiadomości, kanały i osoby zapisane na telefonie; wyniki z fragmentem treści otwierają rozmowę. Dostępne z nagłówka listy rozmów.
- **Przypinanie i archiwum**: przypięte rozmowy trzymają się góry listy, archiwum chowa resztę (nadal widoczne w szukaniu). Przytrzymanie rozmowy otwiera jej opcje.
- **Mapa okolicy**: osoby, które przysłały pozycję, naniesione względem ciebie – odległość, kierunek, skala i kierunki świata. Rysowana z samych współrzędnych, bez kafelków i bez internetu. Dotknięcie punktu otwiera profil, „Nawiguj” przekazuje punkt do map offline.
- **Latarka alarmowa**: miganie diodą i ekranem wzorem SOS alfabetem Morse'a, sygnałem alpejskim (6/min), szybkimi mignięciami albo światłem ciągłym. Ekran nie gaśnie w trakcie nadawania.
- **Eksport historii**: zapis jednej rozmowy albo całej historii do pliku tekstowego (czas, autor, pozycje w stopniach i w formacie dziesiętnym) przez systemowe okno „Udostępnij”. Pliki eksportu same znikają po dobie.
- Powrót z nowych ekranów obsługuje systemowy gest wstecz; stare eksporty nie zostają w pamięci podręcznej.

## 0.1.0

Pierwsze wydanie OpenMesh – czatu bez internetu przez Bluetooth i Wi‑Fi Direct.

- **Sieć mesh**: automatyczne wykrywanie i łączenie telefonów (Nearby Connections: Bluetooth, BLE, Wi‑Fi Direct / hotspot), przekazywanie wiadomości przez kolejne urządzenia z licznikiem skoków i usuwaniem duplikatów.
- **Store-and-forward**: wiadomości czekają w kolejce bez zasięgu i wychodzą przy pierwszym kontakcie; ostatnie pakiety trafiają do każdego nowego sąsiada.
- **Kanały**: publiczny #ogólny, własne kanały jawne i chronione hasłem (PBKDF2 + AES-256-GCM), lista kanałów widzianych w pobliżu.
- **Rozmowy prywatne** szyfrowane end-to-end (ECDH P-256 + AES-256-GCM) z potwierdzeniem doręczenia.
- **Podpisy ECDSA** każdej wiadomości – nie da się podszyć pod inną osobę; odcisk klucza do weryfikacji.
- **SOS** z pozycją GPS (działa offline), maksymalnym zasięgiem, alarmem dźwiękowym i wysyłaniem przez przytrzymanie.
- **Pozycja**: udostępnianie współrzędnych, odległość i kierunek do innych, otwieranie w aplikacji map (także offline).
- **W pobliżu**: radar sieci, lista osób z liczbą skoków, statusem, poziomem baterii i odległością.
- **Szybkie odpowiedzi**, szkice wiadomości, grupowanie dymków, nagłówki dni, udostępnianie tekstu z innych aplikacji.
- **Praca w tle**: usługa pierwszoplanowa, start po uruchomieniu telefonu, powiadomienia o wiadomościach i alarmach SOS.
- **Ustawienia**: zasięg (TTL), czas przechowywania, układ sieci (klaster / gwiazda), przekazywanie, wyciszanie kanałów, blokowanie osób, nowa tożsamość, tryb paniki (usunięcie wszystkich danych).
- **Automatyczne aktualizacje** z GitHub Releases – pobieranie i instalacja jednym dotknięciem albo w pełni automatycznie.
