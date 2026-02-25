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
        log.info("Rozpoczynam wysyłkę e-maila weryfikacyjnego do: %s".formatted(event.getEmail()));
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
            log.info("Wysłano e-mail weryfikacyjny do: %s".formatted(event.getEmail()));
        } catch (Exception e) {
            log.error("Błąd poczas wysyłania maila potwierdzającego do: %s".formatted(event.getEmail()), e);
        }
    }
}
