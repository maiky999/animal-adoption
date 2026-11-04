package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.ConversationPreviewDto;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.User;

import java.util.List;

/**
 * Business logic for chat between users in the context of a listing.
 */
public interface MessageService {

    /**
     * Sends a message - it is saved to the database immediately, so it is visible right after the redirect.
     *
     * @throws IllegalArgumentException if the sender, recipient or listing does not exist
     * @throws SecurityException        if the sender may not write in this conversation
     *                                  (see {@link #canAccessConversation(Ad, User, User)})
     */
    void sendMessage(String senderEmail, Long receiverId, Long adId, String content);

    /**
     * Checks whether the user may open (and write in) the conversation with another user about a listing.
     * <ul>
     *     <li>a conversation always takes place between the listing author and another user,</li>
     *     <li>an existing conversation stays available regardless of the listing status (e.g. after adoption),</li>
     *     <li>a new conversation can only be started by a non-author, about an active listing.</li>
     * </ul>
     */
    boolean canAccessConversation(Ad ad, User currentUser, User otherUser);

    /** Returns the conversation between two users about a listing and marks received messages as read. */
    List<Message> getConversationAndMarkAsRead(String currentUserEmail, Long otherUserId, Long adId);

    /** Returns the user's conversations (last message and number of unread messages). */
    List<ConversationPreviewDto> getUserConversations(String email);

    /** Returns the user's total number of unread messages. */
    long getUnreadMessagesCount(String email);
}
