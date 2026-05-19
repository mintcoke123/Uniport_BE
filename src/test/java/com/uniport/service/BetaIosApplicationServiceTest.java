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
import static org.mockito.Mockito.never;

class BetaIosApplicationServiceTest {

    @Test
    void submitStoresApplicationAndMarksInviteSentWhenAppStoreConnectAcceptsIt() {
        BetaIosApplicationRepository repository = mock(BetaIosApplicationRepository.class);
        AppStoreConnectUserInvitationClient invitationClient = mock(AppStoreConnectUserInvitationClient.class);
        AppStoreConnectBetaGroupClient betaGroupClient = mock(AppStoreConnectBetaGroupClient.class);
        when(repository.findByAppleIdEmail("ios@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(BetaIosApplication.class))).thenAnswer(invocation -> {
            BetaIosApplication application = invocation.getArgument(0);
            application.setId(12L);
            return application;
        });
        when(invitationClient.inviteUser(any(AppStoreConnectUserInvitationRequest.class)))
                .thenReturn(AppStoreConnectUserInvitationResult.sent("invite-123"));
        BetaIosApplicationService service = new BetaIosApplicationService(repository, invitationClient, betaGroupClient);

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
        AppStoreConnectBetaGroupClient betaGroupClient = mock(AppStoreConnectBetaGroupClient.class);
        when(repository.findByAppleIdEmail("ios@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(BetaIosApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationClient.inviteUser(any(AppStoreConnectUserInvitationRequest.class)))
                .thenReturn(AppStoreConnectUserInvitationResult.skipped("App Store Connect API is not configured."));
        BetaIosApplicationService service = new BetaIosApplicationService(repository, invitationClient, betaGroupClient);

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
                mock(AppStoreConnectUserInvitationClient.class),
                mock(AppStoreConnectBetaGroupClient.class)
        );

        assertThrows(ApiException.class, () -> service.submit(BetaIosApplicationRequestDTO.builder()
                .name("김유니")
                .appleIdEmail("ios@example.com")
                .device("iPhone")
                .consent(false)
                .build()));
    }

    @Test
    void syncPendingInternalTestersAddsAcceptedTesterToConfiguredGroup() {
        BetaIosApplicationRepository repository = mock(BetaIosApplicationRepository.class);
        AppStoreConnectUserInvitationClient invitationClient = mock(AppStoreConnectUserInvitationClient.class);
        AppStoreConnectBetaGroupClient betaGroupClient = mock(AppStoreConnectBetaGroupClient.class);
        BetaIosApplication application = BetaIosApplication.builder()
                .id(7L)
                .name("김유니")
                .appleIdEmail("ios@example.com")
                .contactEmail("ios@example.com")
                .device("iPhone")
                .consent(true)
                .status(BetaIosApplicationStatus.USER_INVITE_SENT)
                .build();
        when(repository.findTop50ByStatusInOrderByUpdatedAtAsc(
                java.util.List.of(
                        BetaIosApplicationStatus.USER_INVITE_SENT,
                        BetaIosApplicationStatus.USER_INVITE_FAILED,
                        BetaIosApplicationStatus.TESTFLIGHT_GROUP_FAILED
                )
        )).thenReturn(java.util.List.of(application));
        when(betaGroupClient.addTesterToInternalGroup("ios@example.com"))
                .thenReturn(AppStoreConnectBetaGroupSyncResult.added("tester-1", "group-1"));
        when(repository.save(any(BetaIosApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BetaIosApplicationService service = new BetaIosApplicationService(repository, invitationClient, betaGroupClient);

        service.syncPendingInternalTesters();

        ArgumentCaptor<BetaIosApplication> captor = ArgumentCaptor.forClass(BetaIosApplication.class);
        verify(repository).save(captor.capture());
        BetaIosApplication saved = captor.getValue();
        assertEquals(BetaIosApplicationStatus.TESTFLIGHT_GROUP_ADDED, saved.getStatus());
        assertEquals("tester-1", saved.getBetaTesterId());
        assertEquals("group-1", saved.getTestflightGroupId());
        assertEquals(null, saved.getTestflightGroupFailureMessage());
    }

    @Test
    void syncPendingInternalTestersLeavesApplicationPendingWhenTesterHasNotAcceptedInviteYet() {
        BetaIosApplicationRepository repository = mock(BetaIosApplicationRepository.class);
        AppStoreConnectUserInvitationClient invitationClient = mock(AppStoreConnectUserInvitationClient.class);
        AppStoreConnectBetaGroupClient betaGroupClient = mock(AppStoreConnectBetaGroupClient.class);
        BetaIosApplication application = BetaIosApplication.builder()
                .id(7L)
                .name("김유니")
                .appleIdEmail("ios@example.com")
                .contactEmail("ios@example.com")
                .device("iPhone")
                .consent(true)
                .status(BetaIosApplicationStatus.USER_INVITE_SENT)
                .build();
        when(repository.findTop50ByStatusInOrderByUpdatedAtAsc(
                java.util.List.of(
                        BetaIosApplicationStatus.USER_INVITE_SENT,
                        BetaIosApplicationStatus.USER_INVITE_FAILED,
                        BetaIosApplicationStatus.TESTFLIGHT_GROUP_FAILED
                )
        )).thenReturn(java.util.List.of(application));
        when(betaGroupClient.addTesterToInternalGroup("ios@example.com"))
                .thenReturn(AppStoreConnectBetaGroupSyncResult.pending("No betaTester exists for email yet."));
        BetaIosApplicationService service = new BetaIosApplicationService(repository, invitationClient, betaGroupClient);

        service.syncPendingInternalTesters();

        assertEquals(BetaIosApplicationStatus.USER_INVITE_SENT, application.getStatus());
        assertEquals("No betaTester exists for email yet.", application.getTestflightGroupFailureMessage());
        verify(repository, never()).save(any(BetaIosApplication.class));
    }
}
