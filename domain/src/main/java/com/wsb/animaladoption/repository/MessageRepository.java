package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySenderIdOrReceiverIdOrderBySentAtDesc(Long senderId, Long receiverId);

    @Query("SELECT m FROM Message m WHERE m.relatedAd.id = :adId AND " +
            "((m.sender.id = :user1Id AND m.receiver.id = :user2Id) OR " +
            "(m.sender.id = :user2Id AND m.receiver.id = :user1Id)) " +
            "ORDER BY m.sentAt ASC")
    List<Message> findConversation(@Param("adId") Long adId,
                                   @Param("user1Id") Long user1Id,
                                   @Param("user2Id") Long user2Id);

    long countByReceiverIdAndIsReadFalse(Long receiverId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Message m SET m.isRead = true WHERE m.relatedAd.id = :adId AND m.sender.id = :senderId AND m.receiver.id = :receiverId AND m.isRead = false")
    void markConversationAsRead(@Param("adId") Long adId, @Param("senderId") Long senderId, @Param("receiverId") Long receiverId);
}
