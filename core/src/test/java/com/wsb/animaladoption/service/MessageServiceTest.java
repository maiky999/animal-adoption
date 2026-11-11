package com.wsb.animaladoption.service;

import com.wsb.animaladoption.service.impl.MessageServiceImpl;
import com.wsb.animaladoption.dto.ConversationPreviewDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdRepository adRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    @DisplayName("sendMessage - should save the message in the database immediately")
    void shouldSaveMessageToDatabase() {
        // given
        User sender = User.builder().id(1L).email("sender@example.com").build();
        User receiver = User.builder().id(2L).email("receiver@example.com").build();
        // The recipient is the author of an active listing - a new conversation is allowed
        Ad ad = Ad.builder().id(10L).title("Ogłoszenie testowe").author(receiver).status(AdStatusEnum.ACTIVE).build();

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));

        // when
        messageService.sendMessage("sender@example.com", 2L, 10L, "Wiadomość testowa");

        // then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).save(messageCaptor.capture());

        Message saved = messageCaptor.getValue();
        assertThat(saved.getSender()).isEqualTo(sender);
        assertThat(saved.getReceiver()).isEqualTo(receiver);
        assertThat(saved.getRelatedAd()).isEqualTo(ad);
        assertThat(saved.getContent()).isEqualTo("Wiadomość testowa");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("sendMessage - should throw an exception when the recipient does not exist")
    void shouldThrowExceptionWhenReceiverNotFound() {
        User sender = User.builder().id(1L).email("sender@example.com").build();

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage("sender@example.com", 99L, 10L, "Treść"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie znaleziono odbiorcy");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendMessage - should reject a new conversation about a listing that is not active")
    void shouldRejectNewConversationAboutInactiveAd() {
        User sender = User.builder().id(1L).email("sender@example.com").build();
        User author = User.builder().id(2L).email("author@example.com").build();
        Ad ad = Ad.builder().id(10L).author(author).status(AdStatusEnum.PENDING).build();

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));
        when(messageRepository.existsConversation(10L, 1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.sendMessage("sender@example.com", 2L, 10L, "Hej"))
                .isInstanceOf(SecurityException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("canAccessConversation - an existing conversation stays available after the listing is no longer active")
    void existingConversationShouldStayAvailable() {
        User me = User.builder().id(1L).build();
        User author = User.builder().id(2L).build();
        Ad ad = Ad.builder().id(10L).author(author).status(AdStatusEnum.ADOPTED).build();
        when(messageRepository.existsConversation(10L, 1L, 2L)).thenReturn(true);

        assertThat(messageService.canAccessConversation(ad, me, author)).isTrue();
    }

    @Test
    @DisplayName("canAccessConversation - two strangers cannot talk about someone else's listing, nor can you talk to yourself")
    void conversationMustInvolveTheAuthor() {
        User me = User.builder().id(1L).build();
        User stranger = User.builder().id(3L).build();
        User author = User.builder().id(2L).build();
        Ad ad = Ad.builder().id(10L).author(author).status(AdStatusEnum.ACTIVE).build();

        assertThat(messageService.canAccessConversation(ad, me, stranger)).isFalse();
        assertThat(messageService.canAccessConversation(ad, author, author)).isFalse();
    }

    @Test
    @DisplayName("getConversationAndMarkAsRead - should mark messages as read and return the list")
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
    @DisplayName("getUserConversations - should group messages into threads and count unread ones")
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

    @Test
    @DisplayName("getNewMessagesAndMarkAsRead - returns only newer messages and marks them as read")
    void shouldReturnOnlyNewMessagesAndMarkThemAsRead() {
        User me = User.builder().id(1L).email("me@example.com").build();
        Message newer = Message.builder().id(8L).content("Nowa").build();
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        when(messageRepository.findConversationAfter(10L, 1L, 2L, 7L)).thenReturn(List.of(newer));

        List<Message> result = messageService.getNewMessagesAndMarkAsRead("me@example.com", 2L, 10L, 7L);

        assertThat(result).containsExactly(newer);
        verify(messageRepository).markConversationAsRead(10L, 2L, 1L);
    }

    @Test
    @DisplayName("getNewMessagesAndMarkAsRead - with nothing new, no UPDATE is sent to the database")
    void shouldNotMarkAnythingWhenNoNewMessages() {
        User me = User.builder().id(1L).email("me@example.com").build();
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        when(messageRepository.findConversationAfter(10L, 1L, 2L, 7L)).thenReturn(List.of());

        assertThat(messageService.getNewMessagesAndMarkAsRead("me@example.com", 2L, 10L, 7L)).isEmpty();
        verify(messageRepository, never()).markConversationAsRead(any(), any(), any());
    }
}