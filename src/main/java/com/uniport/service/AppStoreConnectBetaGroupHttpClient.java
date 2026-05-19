package com.uniport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AppStoreConnectBetaGroupHttpClient implements AppStoreConnectBetaGroupClient {

    private final RestTemplate restTemplate;
    private final AppStoreConnectTokenProvider tokenProvider;
    private final String appId;
    private final String configuredGroupId;
    private final String groupName;
    private final String baseUrl;

    public AppStoreConnectBetaGroupHttpClient(
            RestTemplate restTemplate,
            AppStoreConnectTokenProvider tokenProvider,
            @Value("${app.beta.ios.app-store-connect.app-id:}") String appId,
            @Value("${app.beta.ios.app-store-connect.internal-beta-group-id:}") String configuredGroupId,
            @Value("${app.beta.ios.app-store-connect.internal-beta-group-name:uniport tester}") String groupName,
            @Value("${app.beta.ios.app-store-connect.base-url:https://api.appstoreconnect.apple.com}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.appId = trimToEmpty(appId);
        this.configuredGroupId = trimToEmpty(configuredGroupId);
        this.groupName = trimToDefault(groupName, "uniport tester");
        this.baseUrl = trimTrailingSlash(trimToDefault(baseUrl, "https://api.appstoreconnect.apple.com"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppStoreConnectBetaGroupSyncResult addTesterToInternalGroup(String email) {
        if (appId.isBlank()) {
            return AppStoreConnectBetaGroupSyncResult.skipped("App Store Connect TestFlight group sync is not configured.");
        }
        if (configuredGroupId.isBlank() && groupName.isBlank()) {
            return AppStoreConnectBetaGroupSyncResult.skipped("App Store Connect internal beta group is not configured.");
        }

        try {
            HttpEntity<Void> getEntity = new HttpEntity<>(headers());
            ResponseEntity<Map> testerResponse = restTemplate.exchange(
                    betaTesterUrl(email),
                    HttpMethod.GET,
                    getEntity,
                    Map.class
            );
            String betaTesterId = firstDataId(testerResponse.getBody());
            if (betaTesterId == null || betaTesterId.isBlank()) {
                return AppStoreConnectBetaGroupSyncResult.pending("No betaTester exists for email yet.");
            }

            String groupId = configuredGroupId.isBlank() ? resolveGroupId(getEntity) : configuredGroupId;
            if (groupId == null || groupId.isBlank()) {
                return AppStoreConnectBetaGroupSyncResult.failed("App Store Connect internal beta group was not found.");
            }

            RestClientResponseException groupRelationshipFailure = null;
            try {
                HttpEntity<Map<String, Object>> postEntity = new HttpEntity<>(betaTesterRelationshipBody(betaTesterId), headers());
                restTemplate.exchange(
                        baseUrl + "/v1/betaGroups/" + groupId + "/relationships/betaTesters",
                        HttpMethod.POST,
                        postEntity,
                        Map.class
                );
            } catch (RestClientResponseException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    return AppStoreConnectBetaGroupSyncResult.added(betaTesterId, groupId);
                }
                groupRelationshipFailure = e;
            }

            if (groupRelationshipFailure != null) {
                try {
                    HttpEntity<Map<String, Object>> fallbackEntity = new HttpEntity<>(betaGroupRelationshipBody(groupId), headers());
                    restTemplate.exchange(
                            baseUrl + "/v1/betaTesters/" + betaTesterId + "/relationships/betaGroups",
                            HttpMethod.POST,
                            fallbackEntity,
                            Map.class
                    );
                } catch (RestClientResponseException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        return AppStoreConnectBetaGroupSyncResult.added(betaTesterId, groupId);
                    }
                    return AppStoreConnectBetaGroupSyncResult.failed(truncateMessage(
                            "App Store Connect TestFlight group sync failed: groupRelationship="
                                    + responseFailure(groupRelationshipFailure)
                                    + "; testerRelationship="
                                    + responseFailure(e)
                    ));
                }
            }
            return AppStoreConnectBetaGroupSyncResult.added(betaTesterId, groupId);
        } catch (RestClientResponseException e) {
            return AppStoreConnectBetaGroupSyncResult.failed(truncateMessage(
                    "App Store Connect TestFlight group sync failed: " + responseFailure(e)
            ));
        } catch (RestClientException | IllegalStateException e) {
            return AppStoreConnectBetaGroupSyncResult.failed(truncateMessage(
                    "App Store Connect TestFlight group sync failed: " + e.getMessage()
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveGroupId(HttpEntity<Void> entity) {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v1/apps/" + appId + "/betaGroups?limit=200",
                HttpMethod.GET,
                entity,
                Map.class
        );
        Object data = response.getBody() != null ? response.getBody().get("data") : null;
        if (!(data instanceof List<?> groups)) {
            return null;
        }
        String normalizedGroupName = groupName.toLowerCase(Locale.ROOT);
        return groups.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(group -> normalizedGroupName.equals(groupNameOf(group).toLowerCase(Locale.ROOT)))
                .map(group -> Objects.toString(group.get("id"), ""))
                .filter(id -> !id.isBlank())
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static String groupNameOf(Map<?, ?> group) {
        Object attributes = group.get("attributes");
        if (attributes instanceof Map<?, ?> attributesMap) {
            return Objects.toString(attributesMap.get("name"), "");
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String firstDataId(Map<String, Object> body) {
        Object data = body != null ? body.get("data") : null;
        if (!(data instanceof List<?> items) || items.isEmpty()) {
            return null;
        }
        Object first = items.get(0);
        if (first instanceof Map<?, ?> firstMap) {
            return Objects.toString(firstMap.get("id"), "");
        }
        return null;
    }

    private Map<String, Object> betaTesterRelationshipBody(String betaTesterId) {
        return Map.of(
                "data", List.of(Map.of(
                        "type", "betaTesters",
                        "id", betaTesterId
                ))
        );
    }

    private Map<String, Object> betaGroupRelationshipBody(String groupId) {
        return Map.of(
                "data", List.of(Map.of(
                        "type", "betaGroups",
                        "id", groupId
                ))
        );
    }

    private static String responseFailure(RestClientResponseException e) {
        String responseBody = normalizeWhitespace(e.getResponseBodyAsString());
        if (responseBody.isBlank()) {
            return e.getStatusCode().toString();
        }
        return e.getStatusCode() + " body=" + responseBody;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String truncateMessage(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenProvider.createToken());
        return headers;
    }

    private String betaTesterUrl(String email) {
        return baseUrl + "/v1/betaTesters?filter%5Bemail%5D="
                + URLEncoder.encode(email, StandardCharsets.UTF_8);
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
