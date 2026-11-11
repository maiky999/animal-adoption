# Łapka – portal adopcji zwierząt

Aplikacja webowa, w której osoby prywatne i organizacje (schroniska, fundacje) publikują ogłoszenia
o zwierzętach do adopcji, a zainteresowani mogą się z nimi skontaktować przez czat lub ankietę przedadopcyjną.
Projekt powstał jako praca inżynierska (WSB).

## Funkcje

- rejestracja z weryfikacją adresu e-mail, logowanie z opcją „Zapamiętaj mnie”,
- ogłoszenia ze zdjęciami, filtrowanie po kategoriach, moderacja przed publikacją,
- czat między użytkownikami w kontekście ogłoszenia,
- ankieta przedadopcyjna wysyłana e-mailem do organizacji,
- artykuły publikowane przez moderatorów,
- profil użytkownika z awatarem i profilem publicznym,
- wersja polska i angielska interfejsu.

## Technologie

Java 21, Spring Boot 4 (Web MVC, Security, Data JPA, Validation, Mail, AMQP), Thymeleaf + htmx,
Microsoft SQL Server, RabbitMQ, Maven, Docker Compose. Testy: JUnit 5, Mockito, H2.

## Struktura modułów

| Moduł          | Zawartość                                                                  |
|----------------|----------------------------------------------------------------------------|
| `translations` | pliki tłumaczeń (`i18n/*/messages*.properties`) i `TranslationService`     |
| `domain`       | encje JPA, repozytoria, enumy, szyfrowanie pól w bazie                     |
| `core`         | logika biznesowa (serwisy), DTO, konfiguracja RabbitMQ, wysyłka e-maili    |
| `web`          | kontrolery, konfiguracja Spring Security, szablony Thymeleaf, `application.yml` |

Zależności idą w jedną stronę: `web` → `core` → `domain`; `translations` jest używany przez `core` i `web`.

## Uruchomienie lokalne

Wymagania: JDK 21, Docker (do bazy danych i RabbitMQ).

1. **Zmienne środowiskowe.** Wszystkie ustawienia są w pliku `system_variables.env`
   (opis każdej zmiennej znajduje się w komentarzach). Przed uruchomieniem uzupełnij przynajmniej
   `DB_PASSWORD`, dane poczty (`MAIL_USERNAME`, `MAIL_PASSWORD`) oraz klucze `REMEMBER_ME_KEY` i `CRYPTO_KEY`.

   **Pierwszy administrator.** Ustaw `INITIAL_ADMIN_EMAIL` i `INITIAL_ADMIN_PASSWORD` (min. 12 znaków).
   Przy starcie aplikacja zakłada to konto tylko wtedy, gdy w bazie nie ma jeszcze żadnego administratora.
   Jeśli konto z tym e-mailem już istnieje, dostaje rolę administratora, a jego hasło się nie zmienia.
   Kolejne role (moderator, administrator) nadaje już administrator na profilu użytkownika
   (`/users/{id}`, sekcja „Rola w serwisie”). Po pierwszym uruchomieniu obie zmienne można usunąć.

2. **Baza danych i RabbitMQ:**

   ```bash
   docker compose --env-file system_variables.env up -d
   ```

   Uruchamia SQL Server (port 1433, baza tworzona automatycznie) i RabbitMQ
   (port 5672, panel administracyjny: http://localhost:15672). Porty są dostępne tylko z tego
   komputera (`127.0.0.1`), nie z sieci.

3. **Aplikacja:**
   - w IntelliJ IDEA – konfiguracja uruchomieniowa **„Łapka - aplikacja”** (wymaga wtyczki *EnvFile*,
     która wczytuje `system_variables.env`),
   - albo z wiersza poleceń, po ustawieniu zmiennych z `system_variables.env` w środowisku:

     ```bash
     ./mvnw install -DskipTests
     ./mvnw -pl web spring-boot:run
     ```

   Aplikacja działa pod adresem http://localhost:8080. Tabele tworzy Hibernate (`ddl-auto: update`),
   a kategorie zwierząt są dodawane z `web/src/main/resources/data.sql`.

## Wdrożenie na serwer (VPS)

Na serwerze całość działa w Dockerze (`docker-compose.prod.yml`): aplikacja, SQL Server (edycja Express),
RabbitMQ i [Caddy](https://caddyserver.com), który przyjmuje ruch z internetu i sam pobiera certyfikat HTTPS
z Let's Encrypt. Z zewnątrz dostępne są tylko porty 80 i 443. Baza danych i RabbitMQ nie wystawiają żadnych portów.

| Plik                       | Rola                                                                     |
|----------------------------|--------------------------------------------------------------------------|
| `Dockerfile`               | buduje obraz aplikacji (etap 1: Maven buduje jar, etap 2: tylko JRE + jar) |
| `docker-compose.prod.yml`  | wszystkie usługi, wolumeny na dane, zdjęcia i certyfikaty                |
| `Caddyfile`                | reverse proxy z automatycznym HTTPS                                      |
| `.env.production.example`  | wzór pliku `.env` z hasłami i kluczami (opis w komentarzach)             |

**Wymagania:** VPS z procesorem x86-64 (obraz SQL Server nie działa na ARM), co najmniej 4 GB RAM,
Ubuntu 22.04/24.04, otwarte porty 22, 80 i 443. Do wysyłki e-maili potrzebne jest konto Gmail
z [hasłem aplikacji](https://myaccount.google.com/apppasswords). Niektórzy dostawcy (np. DigitalOcean)
domyślnie blokują wychodzący SMTP. Wtedy maile z linkiem aktywacyjnym nie dotrą.

1. **Docker** (na serwerze):

   ```bash
   curl -fsSL https://get.docker.com | sh
   ```

2. **Kod i konfiguracja:**

   ```bash
   git clone <adres-repozytorium> lapka && cd lapka
   cp .env.production.example .env
   nano .env        # uzupełnij wszystkie wartości
   ```

   `DOMAIN` to własna domena z rekordem A wskazującym na IP serwera albo, bez domeny, darmowy adres
   [sslip.io](https://sslip.io), czyli IP serwera z myślnikami, np. `203-0-113-10.sslip.io`.

3. **Uruchomienie:**

   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   docker compose -f docker-compose.prod.yml logs -f app     # podgląd logów (Ctrl+C kończy podgląd)
   ```

   Pierwsze uruchomienie trwa kilka minut (pobieranie obrazów i budowanie). Strona jest dostępna pod
   `https://<DOMAIN>`, a konto administratora powstaje z `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`.

4. **Aktualizacja** po wypchnięciu zmian do repozytorium (na serwerze, w katalogu `lapka`):

   ```bash
   bash deploy.sh
   ```

   Skrypt pobiera kod (`git pull`), przebudowuje i podmienia kontener aplikacji, czeka na start Springa
   i sprawdza, czy strona odpowiada przez HTTPS. Ostrzega też o zmiennych dodanych do `.env.production.example`,
   których brakuje w `.env`. Gdy aplikacja nie wstanie, pokazuje logi i polecenie powrotu do poprzedniej wersji.

   Dane (baza, zdjęcia, certyfikaty) są w wolumenach Dockera i przetrwają przebudowanie kontenerów.
   Nie używaj `down -v`, bo to polecenie usuwa wolumeny razem z danymi.

**Połączenie z bazą na serwerze.** Baza nie jest dostępna z internetu, tylko na serwerze pod `127.0.0.1:1433`.
Z własnego komputera łączymy się przez tunel SSH (okno z tunelem musi być otwarte przez cały czas pracy):

```bash
ssh -L 14330:localhost:1433 azureuser@<DOMAIN>
```

W programie do baz (DBeaver, SSMS, IntelliJ) podajemy: host `localhost`, port `14330`, użytkownik `sa`,
hasło `DB_PASSWORD` z `.env` na serwerze, baza `animaladoption`, opcja „Trust server certificate” włączona.
Szybciej, bez tunelu, prosto na serwerze:
`docker compose -f docker-compose.prod.yml exec mssql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -d animaladoption`.

**Ważne:** przechowuj kopię `CRYPTO_KEY` poza serwerem. Bez niego zaszyfrowanych danych w bazie
(telefony, wiadomości czatu) nie da się odczytać.

## Testy

```bash
./mvnw test
```

Testy korzystają z bazy H2 w pamięci i nie wymagają uruchomionego SQL Servera ani RabbitMQ
(komunikaty o braku połączenia z RabbitMQ w logach testów są niegroźne). Nie łączą się też z lokalnym RabbitMQ
z `docker-compose.yml`, nawet gdy jest uruchomiony (`web/src/test/resources/application.properties`).

## Bezpieczeństwo – najważniejsze rozwiązania

- hasła są hashowane algorytmem BCrypt,
- numery telefonów i treść wiadomości są szyfrowane w bazie (AES-256/GCM z losowym IV) –
  `domain/.../security/StringCryptoConverter.java`; **zmiana `CRYPTO_KEY` uniemożliwia odczyt
  wcześniej zapisanych danych**,
- ogłoszenia oczekujące na moderację lub odrzucone widzą tylko autor i moderatorzy,
  a czat jest dostępny tylko między autorem ogłoszenia a zainteresowaną osobą,
- zmiana adresu e-mail wymaga potwierdzenia linkiem wysłanym na nowy adres; do tego czasu
  obowiązuje dotychczasowy adres,
- przesyłane pliki muszą być obrazami (JPG, PNG, GIF, WEBP) i są zapisywane pod losową nazwą,
- e-maile (weryfikacja konta, ankiety) są wysyłane asynchronicznie przez kolejkę RabbitMQ.

### Ochrona przed botami i nadużyciami

- **limity żądań** (`web/.../security/RateLimitFilter.java`): rejestracja 5/h na IP, logowanie 10/min na IP,
  wiadomości 20/min, nowe ogłoszenia i ankiety 10/h, zmiany profilu 10/10 min na konto; po przekroczeniu
  odpowiedź HTTP 429. Można wyłączyć zmienną `RATE_LIMIT_ENABLED=false` (np. do testów obciążeniowych),
- **honeypot i minimalny czas wypełnienia** formularza rejestracji (`RegistrationBotGuard`),
- **ALTCHA** – CAPTCHA typu *proof of work* na rejestracji, obsługiwana w całości przez naszą aplikację
  (`AltchaService`, bez zewnętrznych usług i ciasteczek). Widget wymaga bezpiecznego kontekstu
  przeglądarki – działa na `localhost` i przez HTTPS, ale nie przez zwykłe HTTP pod adresem IP w sieci,
- **blokada logowania** na 15 minut po 5 błędnych hasłach do tego samego konta (`LoginAttemptService`),
- **odrzucanie jednorazowych skrzynek e-mail** (`core/src/main/resources/disposable-email-domains.txt`),
- **usuwanie niepotwierdzonych kont** po wygaśnięciu linku aktywacyjnego – co godzinę (`UnverifiedAccountCleanupJob`),
- **limity uploadu**: 10 MB na plik, 30 MB na całe żądanie.

Liczniki są trzymane w pamięci aplikacji, więc działają dla jednej instancji. Za reverse proxy
(nginx, Cloudflare) trzeba ustawić `FORWARD_HEADERS_STRATEGY=native`, żeby limity widziały prawdziwe IP
klienta. Ochrona przed DDoS wolumetrycznym (zalewanie ruchem z wielu maszyn) musi działać przed aplikacją,
np. Cloudflare albo `limit_req` w nginx.
