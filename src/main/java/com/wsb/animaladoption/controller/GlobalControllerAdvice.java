package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {
    private final MessageService messageService;

    @ModelAttribute("messageService")
    public MessageService populateMessageService() {
        return messageService;
    }
}
