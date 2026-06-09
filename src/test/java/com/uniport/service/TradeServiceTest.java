package com.uniport.service;

import com.uniport.dto.OrderResponseDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.dto.TradeRequestDTO;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.Order;
import com.uniport.entity.OrderType;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock MARKET_OPEN_CLOCK = Clock.fixed(Instant.parse("2026-06-09T01:00:00Z"), KOREA_ZONE);
    private static final Clock MARKET_CLOSED_CLOCK = Clock.fixed(Instant.parse("2026-06-09T09:20:00Z"), KOREA_ZONE);

    @Test
    void tradingHoursAllowOnlyRegularMarketSession() {
        assertFalse(TradeService.isTradingHours(LocalTime.of(8, 59)));
        assertTrue(TradeService.isTradingHours(LocalTime.of(9, 0)));
        assertTrue(TradeService.isTradingHours(LocalTime.of(15, 29)));
        assertFalse(TradeService.isTradingHours(LocalTime.of(15, 30)));
        assertFalse(TradeService.isTradingHours(LocalTime.of(18, 20)));
    }

    @Test
    void placeOrderFromSpec_usesExplicitRoomIdWhenUserBelongsToRoom() {
        MatchingRoomMemberRepository memberRepository = mock(MatchingRoomMemberRepository.class);
        when(memberRepository.existsByMatchingRoomIdAndUserId(77L, 5L)).thenReturn(true);
        TeamAccountRepository accountRepository = mock(TeamAccountRepository.class);
        when(accountRepository.findByTeamId(77L)).thenReturn(Optional.empty());
        when(accountRepository.save(any(TeamAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TeamHoldingRepository holdingRepository = mock(TeamHoldingRepository.class);
        when(holdingRepository.findByTeamIdAndStockCode(77L, "005930")).thenReturn(Optional.empty());
        when(holdingRepository.save(any(TeamHolding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KisApiService kisApiService = mock(KisApiService.class);
        when(kisApiService.placeOrder(eq("005930"), eq(2), eq(new BigDecimal("70000")), eq(OrderType.BUY)))
                .thenReturn(OrderResponseDTO.builder().externalOrderNo("stub-order").message("ok").build());

        TradeService tradeService = tradeService(
                orderRepository,
                kisApiService,
                accountRepository,
                holdingRepository,
                memberRepository
        );

        var response = tradeService.placeOrderFromSpec(
                TradeRequestDTO.builder()
                        .stockId(5930L)
                        .side("buy")
                        .quantity(2)
                        .pricePerShare(new BigDecimal("70000"))
                        .roomId("room-77")
                        .build(),
                User.builder().id(5L).nickname("trader").build()
        );

        assertEquals("stub-order", response.getOrderId());
    }

    @Test
    void placeOrderFromSpec_usesOnlyStartedPersonalRoomWhenRoomIdIsOmitted() {
        User user = User.builder().id(5L).nickname("solo-trader").build();
        MatchingRoom room = MatchingRoom.builder()
                .id(77L)
                .name("Solo Room")
                .capacity(1)
                .status("started")
                .build();
        MatchingRoomMember member = MatchingRoomMember.of(room, user);
        MatchingRoomMemberRepository memberRepository = mock(MatchingRoomMemberRepository.class);
        when(memberRepository.findActiveByUserIdOrderByJoinedAtDesc(5L)).thenReturn(List.of(member));
        TeamAccountRepository accountRepository = mock(TeamAccountRepository.class);
        when(accountRepository.findByTeamId(77L)).thenReturn(Optional.empty());
        when(accountRepository.save(any(TeamAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TeamHoldingRepository holdingRepository = mock(TeamHoldingRepository.class);
        when(holdingRepository.findByTeamIdAndStockCode(77L, "005930")).thenReturn(Optional.empty());
        when(holdingRepository.save(any(TeamHolding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KisApiService kisApiService = mock(KisApiService.class);
        when(kisApiService.placeOrder(eq("005930"), eq(2), eq(new BigDecimal("70000")), eq(OrderType.BUY)))
                .thenReturn(OrderResponseDTO.builder().externalOrderNo("solo-order").message("ok").build());

        TradeService tradeService = tradeService(
                orderRepository,
                kisApiService,
                accountRepository,
                holdingRepository,
                memberRepository
        );

        var response = tradeService.placeOrderFromSpec(
                TradeRequestDTO.builder()
                        .stockId(5930L)
                        .side("buy")
                        .quantity(2)
                        .pricePerShare(new BigDecimal("70000"))
                        .build(),
                user
        );

        assertEquals("solo-order", response.getOrderId());
    }

    @Test
    void placeOrderFromSpec_rejectsAfterMarketClose() {
        MatchingRoomMemberRepository memberRepository = mock(MatchingRoomMemberRepository.class);
        when(memberRepository.existsByMatchingRoomIdAndUserId(77L, 5L)).thenReturn(true);
        TradeService tradeService = tradeService(
                mock(OrderRepository.class),
                mock(KisApiService.class),
                mock(TeamAccountRepository.class),
                mock(TeamHoldingRepository.class),
                memberRepository,
                MARKET_CLOSED_CLOCK
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> tradeService.placeOrderFromSpec(
                        TradeRequestDTO.builder()
                                .stockId(5930L)
                                .side("buy")
                                .quantity(2)
                                .pricePerShare(new BigDecimal("70000"))
                                .roomId("room-77")
                                .build(),
                        User.builder().id(5L).nickname("trader").build()
                )
        );

        assertEquals(TradeService.TRADING_HOURS_MESSAGE, exception.getMessage());
    }

    @Test
    void placeOrderFromSpec_rejectsExplicitRoomIdWhenUserIsNotMember() {
        MatchingRoomMemberRepository memberRepository = mock(MatchingRoomMemberRepository.class);
        when(memberRepository.existsByMatchingRoomIdAndUserId(77L, 5L)).thenReturn(false);

        TradeService tradeService = tradeService(
                mock(OrderRepository.class),
                mock(KisApiService.class),
                mock(TeamAccountRepository.class),
                mock(TeamHoldingRepository.class),
                memberRepository
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> tradeService.placeOrderFromSpec(
                        TradeRequestDTO.builder()
                                .stockId(5930L)
                                .side("buy")
                                .quantity(2)
                                .pricePerShare(new BigDecimal("70000"))
                                .roomId("77")
                                .build(),
                        User.builder().id(5L).nickname("trader").build()
                )
        );

        assertEquals("해당 방의 참가자만 거래할 수 있습니다.", exception.getMessage());
    }

    private TradeService tradeService(
            OrderRepository orderRepository,
            KisApiService kisApiService,
            TeamAccountRepository accountRepository,
            TeamHoldingRepository holdingRepository,
            MatchingRoomMemberRepository memberRepository
    ) {
        return tradeService(
                orderRepository,
                kisApiService,
                accountRepository,
                holdingRepository,
                memberRepository,
                MARKET_OPEN_CLOCK
        );
    }

    private TradeService tradeService(
            OrderRepository orderRepository,
            KisApiService kisApiService,
            TeamAccountRepository accountRepository,
            TeamHoldingRepository holdingRepository,
            MatchingRoomMemberRepository memberRepository,
            Clock clock
    ) {
        StockVisualAssetResolver visualAssetResolver = mock(StockVisualAssetResolver.class);
        when(visualAssetResolver.resolve(any(), any(), any(), any()))
                .thenReturn(StockVisualDTO.builder().type("FALLBACK_SYMBOL").text("삼성").build());
        return new TradeService(
                orderRepository,
                kisApiService,
                accountRepository,
                holdingRepository,
                mock(ChatService.class),
                memberRepository,
                visualAssetResolver,
                new StockSymbolLogoUrlResolver("https://uniportbe-production.up.railway.app"),
                clock
        );
    }
}
