package com.wsb.animaladoption.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailListener {

    @RabbitListener(queues = "queue.email.registration")
    public void handleRegistrationEmail(RegistrationEvent event) {
        log.info("Rozpoczynam wysyłkę e-maila weryfikacyjnego do: {}", event.getEmail());
        //TODO: Logika SMTP JavaMailSender.
    }
}
