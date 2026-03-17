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
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AdRepository adRepository;
    private final RabbitTemplate rabbitTemplate;

    public void sendMessage(String senderEmail, Long receiverId, Long adId, String content) {
        ChatMessageEvent event = new ChatMessageEvent(senderEmail, receiverId, adId, content);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_CHAT, RabbitMQConfig.ROUTING_KEY_CHAT, event);
    }

    @Transactional
    public void saveMessageToDatabase(ChatMessageEvent event) {
        User sender = userRepository.findByEmail(event.getSenderEmail())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono nadawcy"));
        User receiver = userRepository.findById(event.getReceiverId())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono odbiorcy"));
        Ad ad = adRepository.findById(event.getAdId())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono ogłoszenia"));

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .relatedAd(ad)
                .content(event.getContent())
                .isRead(false)
                .build();

        messageRepository.saveAndFlush(message);
    }

    @Transactional
    public List<Message> getConversationAndMarkAsRead(String currentUserEmail, Long otherUserId, Long adId) {
        User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow();
        List<Message> conversation = messageRepository.findConversation(adId, currentUser.getId(), otherUserId);
        messageRepository.markConversationAsRead(adId, otherUserId, currentUser.getId());
        return messageRepository.findConversation(adId, currentUser.getId(), otherUserId);
    }

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

    @Transactional(readOnly = true)
    public long getUnreadMessagesCount(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return 0;
        }
        return messageRepository.countByReceiverIdAndIsReadFalse(user.getId());
    }
}
