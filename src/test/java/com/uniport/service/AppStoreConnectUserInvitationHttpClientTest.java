package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppStoreConnectUserInvitationHttpClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void inviteUserPostsScopedUserInvitationToAppStoreConnect() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AppStoreConnectTokenProvider tokenProvider = mock(AppStoreConnectTokenProvider.class);
        when(tokenProvider.createToken()).thenReturn("jwt-token");
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/userInvitations"),
                eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.status(201).body(Map.of(
                "data", Map.of("id", "invitation-1")
        )));
        AppStoreConnectUserInvitationHttpClient client = new AppStoreConnectUserInvitationHttpClient(
                restTemplate,
                tokenProvider,
                true,
                "1234567890",
                "MARKETING",
                "https://api.appstoreconnect.apple.com"
        );

        AppStoreConnectUserInvitationResult result = client.inviteUser(
                new AppStoreConnectUserInvitationRequest("김유니", "ios@example.com")
        );

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.appstoreconnect.apple.com/v1/userInvitations"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class)
        );
        assertEquals("Bearer jwt-token", captor.getValue().getHeaders().getFirst("Authorization"));
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        Map<String, Object> visibleApps = (Map<String, Object>) relationships.get("visibleApps");
        List<Map<String, String>> visibleAppData = (List<Map<String, String>>) visibleApps.get("data");
        assertEquals("userInvitations", data.get("type"));
        assertEquals("ios@example.com", attributes.get("email"));
        assertEquals(List.of("MARKETING"), attributes.get("roles"));
        assertEquals(false, attributes.get("allAppsVisible"));
        assertEquals("1234567890", visibleAppData.get(0).get("id"));
        assertTrue(result.sent());
        assertEquals("invitation-1", result.invitationId());
    }

    @Test
    void inviteUserTreatsDuplicateInvitationAsSentSoGroupSyncCanContinue() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AppStoreConnectTokenProvider tokenProvider = mock(AppStoreConnectTokenProvider.class);
        when(tokenProvider.createToken()).thenReturn("jwt-token");
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/userInvitations"),
                eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new HttpClientErrorException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Conflict",
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
        AppStoreConnectUserInvitationHttpClient client = new AppStoreConnectUserInvitationHttpClient(
                restTemplate,
                tokenProvider,
                true,
                "1234567890",
                "MARKETING",
                "https://api.appstoreconnect.apple.com"
        );

        AppStoreConnectUserInvitationResult result = client.inviteUser(
                new AppStoreConnectUserInvitationRequest("김유니", "ios@example.com")
        );

        assertTrue(result.sent());
        assertTrue(result.duplicate());
    }
}
