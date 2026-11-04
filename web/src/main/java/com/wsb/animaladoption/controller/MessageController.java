package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.MessageService;
import com.wsb.animaladoption.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        User me;
        User otherUser;
        Ad ad;
        try {
            me = userService.findByEmail(currentUser.getUsername());
            otherUser = userService.findById(otherUserId);
            ad = adService.findById(adId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // Without this check anyone could open /messages/{id}/{authorId} for a hidden listing
        // (e.g. pending moderation) and see its title in the chat header
        if (!messageService.canAccessConversation(ad, me, otherUser)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

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
            try {
                messageService.sendMessage(currentUser.getUsername(), otherUserId, adId, content.trim());
            } catch (IllegalArgumentException | SecurityException e) {
                // A non-existent or forbidden conversation - 404, the same as for the chat view
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return "redirect:/messages/" + adId + "/" + otherUserId + "?t=" + System.currentTimeMillis();
    }
}
