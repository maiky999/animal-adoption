package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.dto.ConversationPreviewDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.MessageRepository;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.service.MessageService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AdRepository adRepository;

    /**
     * The message is saved synchronously, inside the HTTP request. Previously it went through a RabbitMQ queue
     * and was saved in the background - the controller redirected to the chat before the listener saved it,
     * so the just-sent message was sometimes missing on the page (race condition).
     * Saving one row is fast, so there is no benefit from doing it asynchronously.
     */
    @Override
    @Transactional
    public void sendMessage(String senderEmail, Long receiverId, Long adId, String content) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono nadawcy"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono odbiorcy"));
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono ogłoszenia"));

        // Checked here as well (not only in the controller), so the rule cannot be bypassed by any caller
        if (!canAccessConversation(ad, sender, receiver)) {
            throw new SecurityException("Brak dostępu do tej rozmowy");
        }

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .relatedAd(ad)
                .content(content)
                .isRead(false)
                .build();

        messageRepository.save(message);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessConversation(Ad ad, User currentUser, User otherUser) {
        // You cannot talk to yourself
        if (currentUser.getId().equals(otherUser.getId())) {
            return false;
        }
        // One side of the conversation must be the listing author
        Long authorId = ad.getAuthor().getId();
        if (!authorId.equals(currentUser.getId()) && !authorId.equals(otherUser.getId())) {
            return false;
        }
        // An existing conversation is always available (e.g. the author answering, or after the animal was adopted)
        if (messageRepository.existsConversation(ad.getId(), currentUser.getId(), otherUser.getId())) {
            return true;
        }
        // A new conversation: only an interested user writing to the author of an active listing
        return authorId.equals(otherUser.getId()) && ad.getStatus() == AdStatusEnum.ACTIVE;
    }

    @Override
    @Transactional
    public List<Message> getConversationAndMarkAsRead(String currentUserEmail, Long otherUserId, Long adId) {
        User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow();
        // First mark as read, then load - this way the returned messages already have the current isRead flag
        messageRepository.markConversationAsRead(adId, otherUserId, currentUser.getId());
        return messageRepository.findConversation(adId, currentUser.getId(), otherUserId);
    }

    @Override
    @Transactional
    public List<Message> getNewMessagesAndMarkAsRead(String currentUserEmail, Long otherUserId, Long adId,
                                                     Long afterMessageId) {
        User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow();
        List<Message> newMessages = messageRepository.findConversationAfter(
                adId, currentUser.getId(), otherUserId, afterMessageId == null ? 0L : afterMessageId);
        // Marking is skipped when nothing new arrived - most refreshes end here without any UPDATE
        if (!newMessages.isEmpty()) {
            messageRepository.markConversationAsRead(adId, otherUserId, currentUser.getId());
        }
        return newMessages;
    }

    @Override
    public List<ConversationPreviewDto> getUserConversations(String email) {
        User currentUser = userRepository.findByEmail(email).orElseThrow();
        List<Message> allMessages = messageRepository.findBySenderIdOrReceiverIdOrderBySentAtDesc(currentUser.getId(), currentUser.getId());

        Map<String, ConversationPreviewDto> conversations = new LinkedHashMap<>();

        for (Message msg : allMessages) {
            User otherUser = msg.getSender().getId().equals(currentUser.getId()) ? msg.getReceiver() : msg.getSender();
            String key = msg.getRelatedAd().getId() + "_" + otherUser.getId();

            if (!conversations.containsKey(key)) {
                conversations.put(key, new ConversationPreviewDto(msg.getRelatedAd(), otherUser, msg, 0));
            }

            if (!msg.isRead() && msg.getReceiver().getId().equals(currentUser.getId())) {
                conversations.get(key).incrementUnread();
            }
        }

        return new ArrayList<>(conversations.values());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadMessagesCount(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return 0;
        }
        return messageRepository.countByReceiverIdAndIsReadFalse(user.getId());
    }
}
