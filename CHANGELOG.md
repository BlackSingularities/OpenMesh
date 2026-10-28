# Zmiany

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
