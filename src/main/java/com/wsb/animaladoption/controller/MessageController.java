package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final UserRepository userRepository;
    private final AdRepository adRepository;

    @GetMapping
    public String inbox(@AuthenticationPrincipal UserDetails currentUser, Model model) {
        model.addAttribute("conversations", messageService.getUserConversations(currentUser.getUsername()));
        return "messages/list";
    }

    @GetMapping("/{adId}/{otherUserId}")
    public String chatView(@PathVariable Long adId,
                           @PathVariable Long otherUserId,
                           @AuthenticationPrincipal UserDetails currentUser,
                           Model model) {

        User me = userRepository.findByEmail(currentUser.getUsername()).orElseThrow();
        User otherUser = userRepository.findById(otherUserId).orElseThrow();
        Ad ad = adRepository.findById(adId).orElseThrow();

        model.addAttribute("messages", messageService.getConversationAndMarkAsRead(me.getEmail(), otherUserId, adId));
        model.addAttribute("me", me);
        model.addAttribute("otherUser", otherUser);
        model.addAttribute("ad", ad);

        return "messages/chat";
    }

    @PostMapping("/{adId}/{otherUserId}")
    public String sendMessage(@PathVariable Long adId,
                              @PathVariable Long otherUserId,
                              @RequestParam("content") String content,
                              @AuthenticationPrincipal UserDetails currentUser) {

        if (content != null && !content.trim().isEmpty()) {
            messageService.sendMessage(currentUser.getUsername(), otherUserId, adId, content.trim());
        }
        return "redirect:/messages/" + adId + "/" + otherUserId + "?t=" + System.currentTimeMillis();
    }
}
