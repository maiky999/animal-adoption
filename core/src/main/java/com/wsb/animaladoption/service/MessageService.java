package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.ConversationPreviewDto;
import com.wsb.animaladoption.event.ChatMessageEvent;
import com.wsb.animaladoption.model.Message;

import java.util.List;

/**
 * Business logic for chat between users in the context of a listing.
 */
public interface MessageService {

    /** Sends a message asynchronously - it goes to the queue and is saved to the database in the background. */
    void sendMessage(String senderEmail, Long receiverId, Long adId, String content);

    /** Saves a message received from the queue to the database. */
    void saveMessageToDatabase(ChatMessageEvent event);

    /** Returns the conversation between two users about a listing and marks received messages as read. */
    List<Message> getConversationAndMarkAsRead(String currentUserEmail, Long otherUserId, Long adId);

    /** Returns the user's conversations (last message and number of unread messages). */
    List<ConversationPreviewDto> getUserConversations(String email);

    /** Returns the user's total number of unread messages. */
    long getUnreadMessagesCount(String email);
}
