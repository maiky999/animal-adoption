package com.wsb.animaladoption.event;

import com.wsb.animaladoption.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailListener {

    private final JavaMailSender mailSender;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_REGISTRATION)
    public void handleRegistrationEmail(RegistrationEvent event) {
        log.info("Rozpoczynam wysyłkę e-maila weryfikacyjnego do: {}", event.getEmail());
        try {
            String verificationLink = "http://localhost:8080/verify?token=" + event.getVerificationToken();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getEmail());
            message.setSubject("Potwierdzenie rejestracji w Łapce \uD83D\uDC3E");
            message.setText("""
                    Hej!\n
                    Miło nam słyszeć, że chcesz dołączyć do grona Łapki!
                    W celu dokończenia rejestracji i aktywacji konta na naszym portalu kliknij w link potwierdzający:\n
                    %s\n
                    Konto możesz aktywować przez następne 24 godziny. Po tym czasie link potwierdzający wygaśnie.\n
                    Pozdrawiamy,\n
                    Ekipa Łapki!
                    """.formatted(verificationLink));

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
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getOrganizationEmail());
            // Ustawiamy Reply-To na maila użytkownika, aby fundacja mogła łatwo odpisać
            message.setReplyTo(event.getContactEmail());
            message.setSubject("Nowa ankieta przedadopcyjna: " + event.getAdTitle());
            message.setText("""
                    Witaj %s,\n
                    Otrzymałeś nową ankietę przedadopcyjną dotyczącą ogłoszenia: "%s".\n
                    
                    --- DANE KANDYDATA ---
                    Imię i nazwisko: %s %s
                    Rok urodzenia: %d
                    Adres: %s, %s
                    Telefon: %s
                    E-mail: %s
                    
                    Kandydat wyraził zgodę na przetwarzanie danych osobowych.
                    Możesz odpowiedzieć bezpośrednio na tę wiadomość, aby skontaktować się z kandydatem.
                    
                    Pozdrawiamy,
                    Zespół portalu Łapka
                    """.formatted(
                    event.getOrganizationName(),
                    event.getAdTitle(),
                    event.getFirstName(), event.getLastName(),
                    event.getBirthYear(),
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
}