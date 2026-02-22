package com.uniport.config;

import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.websocket.ChatWebSocketHandler;
import com.uniport.websocket.GroupChatBroadcaster;
import com.uniport.websocket.PriceBroadcaster;
import com.uniport.websocket.PriceWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 설정.
 * - 그룹 채팅: /groups/{groupId}/chat?token=JWT
 * - 실시간 시세: /prices (클라이언트가 subscribe 메시지로 종목코드 전송)
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatService chatService;
    private final AuthService authService;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final PriceBroadcaster priceBroadcaster;

    public WebSocketConfig(ChatService chatService, AuthService authService,
                           MatchingRoomMemberRepository matchingRoomMemberRepository,
                           PriceBroadcaster priceBroadcaster) {
        this.chatService = chatService;
        this.authService = authService;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.priceBroadcaster = priceBroadcaster;
    }

    @Bean
    public GroupChatBroadcaster groupChatBroadcaster() {
        return new GroupChatBroadcaster();
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(GroupChatBroadcaster groupChatBroadcaster) {
        return new ChatWebSocketHandler(chatService, authService, matchingRoomMemberRepository, groupChatBroadcaster);
    }

    @Bean
    public PriceWebSocketHandler priceWebSocketHandler() {
        return new PriceWebSocketHandler(priceBroadcaster);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler(), "/groups/*/chat")
                .setAllowedOrigins("*");
        registry.addHandler(priceWebSocketHandler(), "/prices")
                .setAllowedOrigins("*");
    }
}
