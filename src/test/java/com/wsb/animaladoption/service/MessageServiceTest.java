package com.wsb.animaladoption.service;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.ConversationPreviewDto;
import com.wsb.animaladoption.event.ChatMessageEvent;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.MessageRepository;
import com.wsb.animaladoption.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdRepository adRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("sendMessage - powinien opublikować zdarzenie na kolejce RabbitMQ")
    void shouldPublishMessageEventToRabbitMQ() {
        // when
        messageService.sendMessage("sender@example.com", 2L, 10L, "Dzień dobry, czy piesek jest dostępny?");

        // then
        ArgumentCaptor<ChatMessageEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_CHAT),
                eq(RabbitMQConfig.ROUTING_KEY_CHAT),
                eventCaptor.capture()
        );

        ChatMessageEvent captured = eventCaptor.getValue();
        assertThat(captured.getSenderEmail()).isEqualTo("sender@example.com");
        assertThat(captured.getReceiverId()).isEqualTo(2L);
        assertThat(captured.getAdId()).isEqualTo(10L);
        assertThat(captured.getContent()).isEqualTo("Dzień dobry, czy piesek jest dostępny?");
    }

    @Test
    @DisplayName("saveMessageToDatabase - powinien poprawnie zapisać wiadomość w bazie")
    void shouldSaveMessageToDatabase() {
        // given
        ChatMessageEvent event = new ChatMessageEvent("sender@example.com", 2L, 10L, "Wiadomość testowa");
        User sender = User.builder().id(1L).email("sender@example.com").build();
        User receiver = User.builder().id(2L).email("receiver@example.com").build();
        Ad ad = Ad.builder().id(10L).title("Ogłoszenie testowe").build();

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));

        // when
        messageService.saveMessageToDatabase(event);

        // then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).saveAndFlush(messageCaptor.capture());

        Message saved = messageCaptor.getValue();
        assertThat(saved.getSender()).isEqualTo(sender);
        assertThat(saved.getReceiver()).isEqualTo(receiver);
        assertThat(saved.getRelatedAd()).isEqualTo(ad);
        assertThat(saved.getContent()).isEqualTo("Wiadomość testowa");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("saveMessageToDatabase - powinien rzucić wyjątek gdy odbiorca nie istnieje")
    void shouldThrowExceptionWhenReceiverNotFound() {
        ChatMessageEvent event = new ChatMessageEvent("sender@example.com", 99L, 10L, "Treść");
        User sender = User.builder().id(1L).email("sender@example.com").build();

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.saveMessageToDatabase(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie znaleziono odbiorcy");

        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getConversationAndMarkAsRead - powinien oznaczyć wiadomości jako przeczytane i zwrócić listę")
    void shouldGetConversationAndMarkAsRead() {
        // given
        User me = User.builder().id(1L).email("me@example.com").build();
        Message msg = Message.builder().id(100L).content("Cześć").build();

        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        when(messageRepository.findConversation(5L, 1L, 2L)).thenReturn(List.of(msg));

        // when
        List<Message> result = messageService.getConversationAndMarkAsRead("me@example.com", 2L, 5L);

        // then
        assertThat(result).hasSize(1);
        verify(messageRepository, times(1)).markConversationAsRead(5L, 2L, 1L);
    }

    @Test
    @DisplayName("getUserConversations - powinien pogrupować wiadomości w wątki i policzyć nieprzeczytane")
    void shouldGroupConversationsAndCountUnread() {
        // given
        User me = User.builder().id(1L).email("me@example.com").build();
        User otherUser = User.builder().id(2L).email("other@example.com").build();
        Ad ad = Ad.builder().id(10L).title("Piesek").build();

        Message msg1 = Message.builder()
                .sender(otherUser).receiver(me).relatedAd(ad).content("Hej 1").isRead(false).build();
        Message msg2 = Message.builder()
                .sender(otherUser).receiver(me).relatedAd(ad).content("Hej 2").isRead(false).build();

        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        when(messageRepository.findBySenderIdOrReceiverIdOrderBySentAtDesc(1L, 1L))
                .thenReturn(List.of(msg1, msg2));

        // when
        List<ConversationPreviewDto> conversations = messageService.getUserConversations("me@example.com");

        // then
        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).getUnreadCount()).isEqualTo(2);
        assertThat(conversations.get(0).getOtherUser()).isEqualTo(otherUser);
    }
}