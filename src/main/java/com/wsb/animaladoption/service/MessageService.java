package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.ConversationPreviewDto;
import com.wsb.animaladoption.event.ChatMessageEvent;
import com.wsb.animaladoption.model.Message;

import java.util.List;

/**
 * Logika biznesowa czatu między użytkownikami w kontekście ogłoszenia.
 */
public interface MessageService {

    /** Wysyła wiadomość asynchronicznie - trafia ona do kolejki, a zapis do bazy odbywa się w tle. */
    void sendMessage(String senderEmail, Long receiverId, Long adId, String content);

    /** Zapisuje w bazie wiadomość odebraną z kolejki. */
    void saveMessageToDatabase(ChatMessageEvent event);

    /** Zwraca rozmowę dwóch użytkowników dotyczącą ogłoszenia i oznacza odebrane wiadomości jako przeczytane. */
    List<Message> getConversationAndMarkAsRead(String currentUserEmail, Long otherUserId, Long adId);

    /** Zwraca listę rozmów użytkownika (ostatnia wiadomość i liczba nieprzeczytanych). */
    List<ConversationPreviewDto> getUserConversations(String email);

    /** Zwraca łączną liczbę nieprzeczytanych wiadomości użytkownika. */
    long getUnreadMessagesCount(String email);
}
