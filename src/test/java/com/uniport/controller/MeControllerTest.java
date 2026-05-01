package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.AuthUserDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MeService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private MeService meService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private MatchingRoomService matchingRoomService;

    @Mock
    private RankingService rankingService;

    @InjectMocks
    private MeController meController;

    @Test
    void getMe_resolvesCurrentUserThroughCurrentUserResolver() {
        User user = User.builder().id(1L).nickname("tester").build();
        FirebaseAuthenticatedUser principal = new FirebaseAuthenticatedUser(user, "firebase-uid", null);
        AuthUserDTO profile = AuthUserDTO.builder()
                .id("1")
                .nickname("tester")
                .totalAssets(BigDecimal.ZERO)
                .investmentAmount(BigDecimal.ZERO)
                .profitLoss(BigDecimal.ZERO)
                .profitLossRate(BigDecimal.ZERO)
                .role("user")
                .build();

        when(currentUserResolver.resolveNullable(principal, "Bearer token")).thenReturn(user);
        when(meService.getProfile(user)).thenReturn(profile);

        ResponseEntity<AuthUserDTO> response = meController.getMe(principal, "Bearer token");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(profile, response.getBody());
        verify(currentUserResolver).resolveNullable(principal, "Bearer token");
        verify(meService).getProfile(user);
    }

    @Test
    void getMatchingRooms_returnsEmptyListWhenAnonymous() {
        when(currentUserResolver.resolveNullable(null, null)).thenReturn(null);

        ResponseEntity<List<Map<String, Object>>> response = meController.getMyMatchingRooms(null, null);

        assertEquals(List.of(), response.getBody());
    }

    @Test
    void getInvestment_usesResolvedUser() {
        User user = User.builder().id(4L).build();
        MyInvestmentResponseDTO expected = MyInvestmentResponseDTO.builder().build();

        when(currentUserResolver.resolveNullable(null, "Bearer token")).thenReturn(user);
        when(meService.getMyInvestment(user)).thenReturn(expected);

        ResponseEntity<MyInvestmentResponseDTO> response = meController.getInvestment(null, "Bearer token");

        assertEquals(expected, response.getBody());
        verify(meService).getMyInvestment(user);
    }
}
