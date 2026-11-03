package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.UserService;
import com.wsb.animaladoption.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private UserService userService;

    @Mock
    private AdService adService;

    @InjectMocks
    private MessageController messageController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserDetails mockUser = org.springframework.security.core.userdetails.User
                .withUsername("test@example.com")
                .password("password")
                .roles("USER")
                .build();

        // Resolver that injects the mocked UserDetails into @AuthenticationPrincipal
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return mockUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(messageController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("GET /messages - should return the inbox with the list of conversations")
    void shouldReturnInboxView() throws Exception {
        when(messageService.getUserConversations("test@example.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/messages"))
                .andExpect(status().isOk())
                .andExpect(view().name("messages/list"))
                .andExpect(model().attributeExists("conversations"));

        verify(messageService, times(1)).getUserConversations("test@example.com");
    }

    @Test
    @DisplayName("GET /messages/{adId}/{otherUserId} - should display the chat view with the model")
    void shouldReturnChatView() throws Exception {
        User me = User.builder().id(1L).email("test@example.com").displayName("Ja").build();
        User otherUser = User.builder().id(2L).email("other@example.com").displayName("Inny").build();
        Ad ad = Ad.builder().id(10L).title("Pies").build();

        when(userService.findByEmail("test@example.com")).thenReturn(me);
        when(userService.findById(2L)).thenReturn(otherUser);
        when(adService.findById(10L)).thenReturn(ad);
        when(messageService.getConversationAndMarkAsRead("test@example.com", 2L, 10L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/messages/10/2"))
                .andExpect(status().isOk())
                .andExpect(view().name("messages/chat"))
                .andExpect(model().attribute("me", me))
                .andExpect(model().attribute("otherUser", otherUser))
                .andExpect(model().attribute("ad", ad))
                .andExpect(model().attributeExists("messages"));

        verify(messageService, times(1)).getConversationAndMarkAsRead("test@example.com", 2L, 10L);
    }

    @Test
    @DisplayName("POST /messages/{adId}/{otherUserId} - should send the message and redirect")
    void shouldSendMessageSuccessfully() throws Exception {
        mockMvc.perform(post("/messages/10/2")
                        .param("content", "Cześć, chętnie zaadoptuję pieska!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/messages/10/2?t=*"));

        verify(messageService, times(1))
                .sendMessage("test@example.com", 2L, 10L, "Cześć, chętnie zaadoptuję pieska!");
    }

    @Test
    @DisplayName("POST /messages/{adId}/{otherUserId} - should not send an empty message")
    void shouldNotSendEmptyMessage() throws Exception {
        mockMvc.perform(post("/messages/10/2")
                        .param("content", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/messages/10/2?t=*"));

        verify(messageService, never()).sendMessage(any(), any(), any(), any());
    }
}