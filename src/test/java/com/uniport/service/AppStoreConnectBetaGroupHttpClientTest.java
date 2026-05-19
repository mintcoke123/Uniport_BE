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

class AppStoreConnectBetaGroupHttpClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void addTesterToInternalGroupFindsTesterResolvesGroupByNameAndPostsRelationship() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AppStoreConnectTokenProvider tokenProvider = mock(AppStoreConnectTokenProvider.class);
        when(tokenProvider.createToken()).thenReturn("jwt-token");
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/betaTesters?filter%5Bemail%5D=ios%40example.com"),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "data", List.of(Map.of("id", "tester-1"))
        )));
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/apps/app-1/betaGroups?limit=200"),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "data", List.of(Map.of(
                        "id", "group-1",
                        "attributes", Map.of("name", "uniport tester")
                ))
        )));
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/betaGroups/group-1/relationships/betaTesters"),
                eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.noContent().build());
        AppStoreConnectBetaGroupHttpClient client = new AppStoreConnectBetaGroupHttpClient(
                restTemplate,
                tokenProvider,
                "app-1",
                "",
                "uniport tester",
                "https://api.appstoreconnect.apple.com"
        );

        AppStoreConnectBetaGroupSyncResult result = client.addTesterToInternalGroup("ios@example.com");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.appstoreconnect.apple.com/v1/betaGroups/group-1/relationships/betaTesters"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class)
        );
        assertEquals("Bearer jwt-token", captor.getValue().getHeaders().getFirst("Authorization"));
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        List<Map<String, String>> data = (List<Map<String, String>>) body.get("data");
        assertEquals("betaTesters", data.get(0).get("type"));
        assertEquals("tester-1", data.get(0).get("id"));
        assertTrue(result.added());
        assertEquals("tester-1", result.betaTesterId());
        assertEquals("group-1", result.groupId());
    }

    @Test
    void addTesterToInternalGroupTreatsExistingRelationshipAsAdded() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AppStoreConnectTokenProvider tokenProvider = mock(AppStoreConnectTokenProvider.class);
        when(tokenProvider.createToken()).thenReturn("jwt-token");
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/betaTesters?filter%5Bemail%5D=ios%40example.com"),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "data", List.of(Map.of("id", "tester-1"))
        )));
        when(restTemplate.exchange(
                eq("https://api.appstoreconnect.apple.com/v1/betaGroups/group-1/relationships/betaTesters"),
                eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new HttpClientErrorException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Conflict",
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
        AppStoreConnectBetaGroupHttpClient client = new AppStoreConnectBetaGroupHttpClient(
                restTemplate,
                tokenProvider,
                "app-1",
                "group-1",
                "",
                "https://api.appstoreconnect.apple.com"
        );

        AppStoreConnectBetaGroupSyncResult result = client.addTesterToInternalGroup("ios@example.com");

        assertTrue(result.added());
        assertEquals("tester-1", result.betaTesterId());
        assertEquals("group-1", result.groupId());
    }
}
