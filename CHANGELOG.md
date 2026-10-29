# Zmiany

## 0.3.3

- Dopasowane kolorystycznie tła ikon aplikacji i spójne zasoby graficzne.

## 0.3.1

- Nowa strona aplikacji z opisem funkcji.
- Jednolita informacja o autorze (Adam Rędzikowski · redzikowski.com) i przycisk „Postaw kawę”.
- Usunięte odnośniki do repozytorium z interfejsu.

## 0.3.0

Zdjęcia i pliki przez sieć mesh.

- **Załączniki**: zdjęcie z galerii albo dowolny plik jedzie w częściach po 12 kB – każda jako osobny podpisany pakiet, więc korzysta z przekazywania przez kolejne telefony i ze store-and-forward dokładnie jak wiadomości.
- **Odbiór bez kolejności**: części mogą dojść w dowolnym porządku i różnymi drogami; po złożeniu plik jest sprawdzany skrótem SHA-256 i dopiero wtedy zapisywany.
- **Brakujące części**: po chwili ciszy aplikacja sama prosi nadawcę o to, co nie doszło; można też dotknąć „Dokończ odbiór” albo nadać plik ponownie z drugiej strony.
- **Szyfrowanie**: załączniki w rozmowach prywatnych i w kanałach z hasłem są szyfrowane tak samo jak tekst (AES-256-GCM), część po części.
- **Zmniejszanie zdjęć**: zdjęcie jest skalowane do 1280 px i kompresowane tak, by zmieściło się w limicie – przez Bluetooth pełna klatka z aparatu nie ma szans.
- **Podgląd w rozmowie**: zdjęcia widnieją w dymku, pliki jako wiersz z nazwą i rozmiarem; w trakcie przesyłania widoczny jest pasek postępu i liczba części.
- **Otwieranie i zapisywanie**: dotknięcie otwiera załącznik w odpowiedniej aplikacji, a „Zapisz lub wyślij dalej” przekazuje go poza OpenMesh. Usunięcie wiadomości usuwa też plik.
- **Ustawienia**: limit wysyłanego pliku (0,5 / 1,5 / 3 MB), przełącznik zmniejszania zdjęć oraz zajęte miejsce z przyciskiem usunięcia pobranych plików.
- Zapis rozmowy w eksporcie wymienia załączniki wraz z rozmiarem.
- Uwaga o zgodności: części załączników rozumieją tylko telefony z 0.3.0 – starsze wersje przekażą zwykłe wiadomości, ale nie pliki.

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
