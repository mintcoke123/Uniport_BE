package com.uniport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AppStoreConnectUserInvitationHttpClient implements AppStoreConnectUserInvitationClient {

    private final RestTemplate restTemplate;
    private final AppStoreConnectTokenProvider tokenProvider;
    private final boolean enabled;
    private final String appId;
    private final String inviteRole;
    private final String baseUrl;

    public AppStoreConnectUserInvitationHttpClient(
            RestTemplate restTemplate,
            AppStoreConnectTokenProvider tokenProvider,
            @Value("${app.beta.ios.app-store-connect.enabled:false}") boolean enabled,
            @Value("${app.beta.ios.app-store-connect.app-id:}") String appId,
            @Value("${app.beta.ios.app-store-connect.invite-role:MARKETING}") String inviteRole,
            @Value("${app.beta.ios.app-store-connect.base-url:https://api.appstoreconnect.apple.com}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.enabled = enabled;
        this.appId = trimToEmpty(appId);
        this.inviteRole = trimToDefault(inviteRole, "MARKETING");
        this.baseUrl = trimTrailingSlash(trimToDefault(baseUrl, "https://api.appstoreconnect.apple.com"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppStoreConnectUserInvitationResult inviteUser(AppStoreConnectUserInvitationRequest request) {
        if (!enabled || appId.isBlank()) {
            return AppStoreConnectUserInvitationResult.skipped("App Store Connect API is not configured.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(tokenProvider.createToken());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody(request), headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/v1/userInvitations",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            Map<String, Object> body = response.getBody();
            Map<String, Object> data = body != null ? (Map<String, Object>) body.get("data") : null;
            String invitationId = data != null ? String.valueOf(data.get("id")) : null;
            return AppStoreConnectUserInvitationResult.sent(invitationId);
        } catch (RestClientResponseException e) {
            return AppStoreConnectUserInvitationResult.failed("App Store Connect invitation failed: " + e.getStatusCode());
        } catch (RestClientException | IllegalStateException e) {
            return AppStoreConnectUserInvitationResult.failed("App Store Connect invitation failed: " + e.getMessage());
        }
    }

    private Map<String, Object> requestBody(AppStoreConnectUserInvitationRequest request) {
        String firstName = trimToDefault(request.displayName(), "Uniport");
        return Map.of(
                "data", Map.of(
                        "type", "userInvitations",
                        "attributes", Map.of(
                                "firstName", firstName,
                                "lastName", "Beta",
                                "email", request.email(),
                                "roles", List.of(inviteRole),
                                "allAppsVisible", false,
                                "provisioningAllowed", false
                        ),
                        "relationships", Map.of(
                                "visibleApps", Map.of(
                                        "data", List.of(Map.of(
                                                "type", "apps",
                                                "id", appId
                                        ))
                                )
                        )
                )
        );
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
