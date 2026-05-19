package com.uniport.service;

import com.uniport.dto.BetaIosApplicationRequestDTO;
import com.uniport.dto.BetaIosApplicationResponseDTO;
import com.uniport.entity.BetaIosApplication;
import com.uniport.entity.BetaIosApplicationStatus;
import com.uniport.exception.ApiException;
import com.uniport.repository.BetaIosApplicationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BetaIosApplicationServiceTest {

    @Test
    void submitStoresApplicationAndMarksInviteSentWhenAppStoreConnectAcceptsIt() {
        BetaIosApplicationRepository repository = mock(BetaIosApplicationRepository.class);
        AppStoreConnectUserInvitationClient invitationClient = mock(AppStoreConnectUserInvitationClient.class);
        when(repository.findByAppleIdEmail("ios@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(BetaIosApplication.class))).thenAnswer(invocation -> {
            BetaIosApplication application = invocation.getArgument(0);
            application.setId(12L);
            return application;
        });
        when(invitationClient.inviteUser(any(AppStoreConnectUserInvitationRequest.class)))
                .thenReturn(AppStoreConnectUserInvitationResult.sent("invite-123"));
        BetaIosApplicationService service = new BetaIosApplicationService(repository, invitationClient);

        BetaIosApplicationResponseDTO response = service.submit(BetaIosApplicationRequestDTO.builder()
                .name(" 김유니 ")
                .appleIdEmail(" IOS@EXAMPLE.COM ")
                .contactEmail(" contact@example.com ")
                .device("iPhone")
                .consent(true)
                .build());

        ArgumentCaptor<BetaIosApplication> captor = ArgumentCaptor.forClass(BetaIosApplication.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        BetaIosApplication saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("김유니", saved.getName());
        assertEquals("ios@example.com", saved.getAppleIdEmail());
        assertEquals("contact@example.com", saved.getContactEmail());
        assertEquals(BetaIosApplicationStatus.USER_INVITE_SENT, saved.getStatus());
        assertEquals("invite-123", saved.getAppStoreConnectInvitationId());
        assertEquals("USER_INVITE_SENT", response.getStatus());
    }

    @Test
    void submitKeepsApplicationWhenAppStoreConnectIsNotConfigured() {
        BetaIosApplicationRepository repository = mock(BetaIosApplicationRepository.class);
        AppStoreConnectUserInvitationClient invitationClient = mock(AppStoreConnectUserInvitationClient.class);
        when(repository.findByAppleIdEmail("ios@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(BetaIosApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationClient.inviteUser(any(AppStoreConnectUserInvitationRequest.class)))
                .thenReturn(AppStoreConnectUserInvitationResult.skipped("App Store Connect API is not configured."));
        BetaIosApplicationService service = new BetaIosApplicationService(repository, invitationClient);

        BetaIosApplicationResponseDTO response = service.submit(BetaIosApplicationRequestDTO.builder()
                .name("김유니")
                .appleIdEmail("ios@example.com")
                .device("iPhone")
                .consent(true)
                .build());

        assertEquals("USER_INVITE_SKIPPED", response.getStatus());
    }

    @Test
    void submitRejectsMissingConsent() {
        BetaIosApplicationService service = new BetaIosApplicationService(
                mock(BetaIosApplicationRepository.class),
                mock(AppStoreConnectUserInvitationClient.class)
        );

        assertThrows(ApiException.class, () -> service.submit(BetaIosApplicationRequestDTO.builder()
                .name("김유니")
                .appleIdEmail("ios@example.com")
                .device("iPhone")
                .consent(false)
                .build()));
    }
}
