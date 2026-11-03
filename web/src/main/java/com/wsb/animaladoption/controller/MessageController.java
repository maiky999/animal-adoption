package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.MessageService;
import com.wsb.animaladoption.service.UserService;
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
    private final UserService userService;
    private final AdService adService;

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

        User me = userService.findByEmail(currentUser.getUsername());
        User otherUser = userService.findById(otherUserId);
        Ad ad = adService.findById(adId);

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
