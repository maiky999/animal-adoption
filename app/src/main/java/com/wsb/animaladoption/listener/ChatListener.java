package com.wsb.animaladoption.listener;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.event.ChatMessageEvent;
import com.wsb.animaladoption.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatListener {
    private final MessageService messageService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CHAT)
    public void handleChatMessage(ChatMessageEvent event) {
        log.info("Odebrano asynchroniczną wiadomość z RabbitMQ. Nadawca: {}", event.getSenderEmail());

        try {
            messageService.saveMessageToDatabase(event);
            log.info("Wiadomość z czatu została pomyślnie zapisana do bazy danych w tle.");
        } catch (Exception e) {
            log.error("Błąd podczas zapisywania asynchronicznej wiadomości do bazy: ", e);
        }
    }
}
