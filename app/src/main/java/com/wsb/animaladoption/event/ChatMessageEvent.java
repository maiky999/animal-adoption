package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent implements Serializable {
    private String senderEmail;
    private Long receiverId;
    private Long adId;
    private String content;
}
