package com.wsb.animaladoption.dto;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationPreviewDto {
    private Ad ad;
    private User otherUser;
    private Message latestMessage;
    private long unreadCount;

    public void incrementUnread() {
        this.unreadCount++;
    }
}
