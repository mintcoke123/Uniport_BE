package com.uniport.config;

import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 설정. 그룹 채팅 엔드포인트: /groups/{groupId}/chat?token=JWT
 * 예: ws://localhost:8080/groups/1/chat?token=eyJ...
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatService chatService;
    private final AuthService authService;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;

    public WebSocketConfig(ChatService chatService, AuthService authService,
                           MatchingRoomMemberRepository matchingRoomMemberRepository) {
        this.chatService = chatService;
        this.authService = authService;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler() {
        return new ChatWebSocketHandler(chatService, authService, matchingRoomMemberRepository);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler(), "/groups/*/chat")
                .setAllowedOrigins("*");
    }
}
