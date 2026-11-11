package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Message;
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

import java.util.List;

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
        ChatParticipants chat = loadAccessibleChat(adId, otherUserId, currentUser);

        List<Message> messages = messageService.getConversationAndMarkAsRead(chat.me().getEmail(), otherUserId, adId);
        model.addAttribute("messages", messages);
        model.addAttribute("me", chat.me());
        model.addAttribute("otherUser", chat.otherUser());
        model.addAttribute("ad", chat.ad());
        model.addAttribute("lastMessageId", lastId(messages, 0L));

        return "messages/chat";
    }

    /**
     * Automatic chat refresh (HTMX asks every few seconds, see messages/chat.html).
     * Returns only the messages that arrived after {@code after} - usually none, so the response is tiny.
     */
    @GetMapping("/{adId}/{otherUserId}/new")
    public String newMessages(@PathVariable Long adId,
                              @PathVariable Long otherUserId,
                              @RequestParam(name = "after", defaultValue = "0") Long after,
                              @AuthenticationPrincipal UserDetails currentUser,
                              Model model) {
        ChatParticipants chat = loadAccessibleChat(adId, otherUserId, currentUser);

        List<Message> newMessages = messageService.getNewMessagesAndMarkAsRead(
                chat.me().getEmail(), otherUserId, adId, after);
        model.addAttribute("newMessages", newMessages);
        model.addAttribute("me", chat.me());
        model.addAttribute("otherUser", chat.otherUser());
        model.addAttribute("ad", chat.ad());
        model.addAttribute("lastMessageId", lastId(newMessages, after));

        return "messages/fragments/chat-messages :: poll";
    }

    /** The unread messages badge in the navigation - refreshed periodically on every page (see layout/navbar.html). */
    @GetMapping("/unread-badge")
    public String unreadBadge() {
        return "layout/navbar :: unread-badge";
    }

    /** Both users and the listing of a conversation, after checking that the current user may see it. */
    private record ChatParticipants(User me, User otherUser, Ad ad) {
    }

    private ChatParticipants loadAccessibleChat(Long adId, Long otherUserId, UserDetails currentUser) {
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
        return new ChatParticipants(me, otherUser, ad);
    }

    /** Id of the newest message on the list - the next refresh asks only for messages after it. */
    private static Long lastId(List<Message> messages, Long fallback) {
        return messages.isEmpty() ? fallback : messages.get(messages.size() - 1).getId();
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
