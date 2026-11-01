package com.wsb.animaladoption.event;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.translations.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailListener {

    private final JavaMailSender mailSender;
    private final TranslationService translationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_REGISTRATION)
    public void handleRegistrationEmail(RegistrationEvent event) {
        log.info("Rozpoczynam wysyłkę e-maila weryfikacyjnego do: {}", event.getEmail());
        try {
            // Listener działa poza żądaniem HTTP, więc język odbiorcy przychodzi w zdarzeniu
            Locale locale = resolveLocale(event.getLanguageTag());
            String verificationLink = "http://localhost:8080/verify?token=" + event.getVerificationToken();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getEmail());
            message.setSubject(translationService.get(locale, "email.registration.subject"));
            message.setText(translationService.get(locale, "email.registration.body", verificationLink));

            mailSender.send(message);
            log.info("Wysłano e-mail weryfikacyjny do: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Błąd poczas wysyłania maila potwierdzającego do: {}", event.getEmail(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ADOPTION_FORM)
    public void handleAdoptionFormEmail(AdoptionFormEvent event) {
        log.info("Rozpoczynam wysyłkę formularza adopcyjnego do fundacji: {}", event.getOrganizationEmail());
        try {
            Locale locale = resolveLocale(event.getLanguageTag());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getOrganizationEmail());
            // Ustawiamy Reply-To na maila użytkownika, aby fundacja mogła łatwo odpisać
            message.setReplyTo(event.getContactEmail());
            message.setSubject(translationService.get(locale, "email.adoption.subject", event.getAdTitle()));
            message.setText(translationService.get(locale, "email.adoption.body",
                    event.getOrganizationName(),
                    event.getAdTitle(),
                    event.getFirstName(), event.getLastName(),
                    // jako tekst, aby MessageFormat nie sformatował roku jak liczby (np. "1 990")
                    String.valueOf(event.getBirthYear()),
                    event.getAddress(), event.getCity(),
                    event.getPhone(),
                    event.getContactEmail()
            ));

            mailSender.send(message);
            log.info("Wysłano formularz adopcyjny do: {}", event.getOrganizationEmail());
        } catch (Exception e) {
            log.error("Błąd podczas wysyłania formularza do: {}", event.getOrganizationEmail(), e);
        }
    }

    /** Zamienia tag języka ze zdarzenia na Locale; dla zdarzeń bez języka (np. starych z kolejki) - język domyślny. */
    private Locale resolveLocale(String languageTag) {
        return languageTag == null || languageTag.isBlank()
                ? translationService.getDefaultLocale()
                : Locale.forLanguageTag(languageTag);
    }
}
