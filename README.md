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

2. **Baza danych i RabbitMQ:**

   ```bash
   docker compose --env-file system_variables.env up -d
   ```

   Uruchamia SQL Server (port 1433, baza tworzona automatycznie) i RabbitMQ
   (port 5672, panel administracyjny: http://localhost:15672).

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

## Testy

```bash
./mvnw test
```

Testy korzystają z bazy H2 w pamięci i nie wymagają uruchomionego SQL Servera ani RabbitMQ
(komunikaty o braku połączenia z RabbitMQ w logach testów są niegroźne).

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
