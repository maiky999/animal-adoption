package com.wsb.translations;

import org.springframework.context.MessageSourceResolvable;

import java.util.List;
import java.util.Locale;

/**
 * Serwis tłumaczeń - jedyny punkt dostępu do tekstów aplikacji w kodzie Java.
 * <p>
 * Teksty są przechowywane jako pary klucz-wartość (np. {@code nav.ads=Ogłoszenia}). Kod aplikacji
 * posługuje się wyłącznie kluczami, a serwis zwraca wartość w odpowiednim języku.
 * Szablony Thymeleaf korzystają z tych samych tłumaczeń bezpośrednio, przez wyrażenia {@code #{klucz}}.
 * <p>
 * Interfejs oddziela aplikację od źródła tłumaczeń. Obecna implementacja czyta pliki {@code .properties},
 * ale można ją zastąpić np. implementacją opartą o bazę danych bez zmian w pozostałych warstwach.
 */
public interface TranslationService {

    /**
     * Zwraca tłumaczenie klucza w języku bieżącego żądania (lub domyślnym, gdy brak żądania).
     *
     * @param key  klucz tłumaczenia, np. {@code "ad.created.pending"}
     * @param args opcjonalne argumenty wstawiane w miejsca {@code {0}}, {@code {1}}...
     * @throws org.springframework.context.NoSuchMessageException gdy klucz nie istnieje
     */
    String get(String key, Object... args);

    /**
     * Zwraca tłumaczenie klucza we wskazanym języku - przydatne poza żądaniem HTTP,
     * np. przy wysyłce e-maili z kolejki.
     */
    String get(Locale locale, String key, Object... args);

    /**
     * Zwraca tłumaczenie obiektu, który sam niesie klucz i argumenty (np. wyjątku biznesowego).
     */
    String get(MessageSourceResolvable resolvable);

    /**
     * Zwraca tłumaczenie klucza lub podaną wartość domyślną, gdy klucz nie istnieje.
     * Przydatne dla danych, które nie zawsze mają tłumaczenie (np. kategorie dodane w bazie).
     */
    String getOrDefault(String key, String defaultValue, Object... args);

    /** Zwraca język bieżącego żądania. */
    Locale getCurrentLocale();

    /** Zwraca język domyślny aplikacji. */
    Locale getDefaultLocale();

    /** Zwraca listę języków obsługiwanych przez aplikację. */
    List<Locale> getSupportedLocales();
}
