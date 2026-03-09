package com.uniport.service.kisws.multi;

import com.uniport.exception.ApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 키 1개당 REST 클라이언트. access token / approval_key 캐시·락·회로차단·레이트리밋.
 */
public class KisRestClient {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String TOKEN_REVOKE_PATH = "/oauth2/revokeP";
    private static final String APPROVAL_PATH = "/oauth2/Approval";
    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 60;
    private static final long APPROVAL_KEY_TTL_MILLIS = 23L * 60 * 60 * 1000;
    private static final long APPROVAL_KEY_REFRESH_BUFFER_MILLIS = 5L * 60 * 1000;

    private final String keyId;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String baseUrlMock;
    private final boolean useMock;
    private final String appkey;
    private final String appsecret;
    private final KeyCircuitBreaker circuitBreaker;
    private final TokenBucketLimiter restLimiter;

    private final AtomicReference<String> cachedAccessToken = new AtomicReference<>();
    private volatile long tokenExpiresAtMillis = 0L;
    private final ReentrantLock tokenIssueLock = new ReentrantLock();

    private volatile String cachedApprovalKey;
    private volatile long approvalKeyExpiresAtMillis = 0L;
    private final ReentrantLock approvalKeyLock = new ReentrantLock();

    public KisRestClient(String keyId, RestTemplate restTemplate,
                         String baseUrl, String baseUrlMock, boolean useMock,
                         String appkey, String appsecret,
                         KeyCircuitBreaker circuitBreaker, TokenBucketLimiter restLimiter) {
        this.keyId = keyId;
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl != null ? baseUrl : "https://openapi.koreainvestment.com:9443";
        this.baseUrlMock = baseUrlMock != null ? baseUrlMock : "https://openapivts.koreainvestment.com:29443";
        this.useMock = useMock;
        this.appkey = appkey != null ? appkey.trim() : "";
        this.appsecret = appsecret != null ? appsecret.trim() : "";
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new KeyCircuitBreaker(keyId);
        this.restLimiter = restLimiter;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getBaseUrl() {
        return useMock ? baseUrlMock : baseUrl;
    }

    public boolean isAvailable() {
        return circuitBreaker.isAvailable();
    }

    /** appkey/appsecret 설정 여부. hasAnyRestClient() 가용 판단용. */
    public boolean isConfigured() {
        return appkey != null && !appkey.isBlank() && appsecret != null && !appsecret.isBlank();
    }

    private void requireRateLimit() {
        if (restLimiter != null && !restLimiter.tryAcquire()) {
            throw new ApiException("KIS REST rate limit exceeded keyId=" + keyId, HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public String getAccessToken() {
        if (appkey.isBlank() || appsecret.isBlank()) {
            throw new ApiException("KIS API appkey/appsecret not configured keyId=" + keyId, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        requireRateLimit();
        if (!circuitBreaker.isAvailable()) {
            throw new ApiException("KIS key circuit open keyId=" + keyId, HttpStatus.SERVICE_UNAVAILABLE);
        }
        long now = System.currentTimeMillis();
        if (cachedAccessToken.get() != null && now < tokenExpiresAtMillis - TOKEN_REFRESH_BUFFER_SECONDS * 1000L) {
            return cachedAccessToken.get();
        }
        tokenIssueLock.lock();
        try {
            if (cachedAccessToken.get() != null && System.currentTimeMillis() < tokenExpiresAtMillis - TOKEN_REFRESH_BUFFER_SECONDS * 1000L) {
                return cachedAccessToken.get();
            }
            String url = getBaseUrl() + TOKEN_PATH;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            Map<String, String> body = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", appkey,
                    "appsecret", appsecret
            );
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() == null) {
                circuitBreaker.onFailure("token null body");
                throw new ApiException("KIS token response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            Map<String, Object> res = response.getBody();
            String accessToken = getString(res, "access_token", null);
            if (accessToken == null || accessToken.isBlank()) {
                accessToken = getString(res, "accessToken", null);
            }
            if (accessToken == null || accessToken.isBlank()) {
                circuitBreaker.onFailure("token empty");
                throw new ApiException("KIS 접근토큰 발급 실패 keyId=" + keyId, HttpStatus.SERVICE_UNAVAILABLE);
            }
            int expiresInSeconds = parseTokenExpiresIn(res);
            cachedAccessToken.set(accessToken);
            tokenExpiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000L;
            circuitBreaker.onSuccess();
            return accessToken;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            circuitBreaker.onFailure("token " + e.getStatusCode());
            throw new ApiException("KIS token request failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            circuitBreaker.onFailure("token");
            throw new ApiException("KIS token request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            tokenIssueLock.unlock();
        }
    }

    public String getApprovalKey() {
        if (appkey.isBlank() || appsecret.isBlank()) {
            throw new ApiException("KIS API appkey/appsecret not configured keyId=" + keyId, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        requireRateLimit();
        if (!circuitBreaker.isAvailable()) {
            throw new ApiException("KIS key circuit open keyId=" + keyId, HttpStatus.SERVICE_UNAVAILABLE);
        }
        long now = System.currentTimeMillis();
        if (cachedApprovalKey != null && !cachedApprovalKey.isBlank()
                && now < (approvalKeyExpiresAtMillis - APPROVAL_KEY_REFRESH_BUFFER_MILLIS)) {
            return cachedApprovalKey;
        }
        approvalKeyLock.lock();
        try {
            now = System.currentTimeMillis();
            if (cachedApprovalKey != null && !cachedApprovalKey.isBlank()
                    && now < (approvalKeyExpiresAtMillis - APPROVAL_KEY_REFRESH_BUFFER_MILLIS)) {
                return cachedApprovalKey;
            }
            String url = getBaseUrl() + APPROVAL_PATH;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            Map<String, String> body = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", appkey,
                    "secretkey", appsecret
            );
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() == null) {
                circuitBreaker.onFailure("approval null body");
                throw new ApiException("KIS approval response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            Map<String, Object> res = response.getBody();
            String approvalKey = getString(res, "approval_key", null);
            if (approvalKey == null || approvalKey.isBlank()) {
                approvalKey = getString(res, "approvalKey", null);
            }
            if (approvalKey == null || approvalKey.isBlank()) {
                circuitBreaker.onFailure("approval empty");
                throw new ApiException("KIS 실시간 접속키 발급 실패 keyId=" + keyId, HttpStatus.SERVICE_UNAVAILABLE);
            }
            cachedApprovalKey = approvalKey;
            approvalKeyExpiresAtMillis = System.currentTimeMillis() + APPROVAL_KEY_TTL_MILLIS;
            circuitBreaker.onSuccess();
            return approvalKey;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            circuitBreaker.onFailure("approval");
            throw new ApiException("KIS approval request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            approvalKeyLock.unlock();
        }
    }

    public HttpHeaders buildAuthHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + getAccessToken());
        headers.set("appkey", appkey);
        headers.set("appsecret", appsecret);
        headers.set("tr_id", trId != null ? trId : "");
        headers.set("custtype", "P");
        return headers;
    }

    /**
     * REST 호출: 레이트 제한·회로 차단 후 인증 헤더로 exchange. 실패 시 onFailure, 성공 시 onSuccess.
     * requestEntity가 null이어도 동작. 기존 헤더는 복사한 뒤 그 위에 auth(Authorization/appkey/appsecret/tr_id/custtype/content-type) set.
     */
    public <T> ResponseEntity<T> exchangeWithAuth(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                                   String trId, ParameterizedTypeReference<T> responseType) {
        requireRateLimit();
        if (!circuitBreaker.isAvailable()) {
            throw new ApiException("KIS key circuit open keyId=" + keyId, HttpStatus.SERVICE_UNAVAILABLE);
        }
        HttpHeaders mergedHeaders = (requestEntity != null && requestEntity.getHeaders() != null)
                ? new HttpHeaders(requestEntity.getHeaders())
                : new HttpHeaders();
        HttpHeaders auth = buildAuthHeaders(trId);
        auth.forEach((name, values) -> mergedHeaders.put(name, values));
        Object body = (requestEntity != null) ? requestEntity.getBody() : null;
        HttpEntity<?> entity = new HttpEntity<>(body, mergedHeaders);
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
            if (response.getStatusCode().is5xxServerError()) {
                circuitBreaker.onFailure("5xx");
            } else {
                circuitBreaker.onSuccess();
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                circuitBreaker.onFailure("5xx");
            } else if (e.getStatusCode().value() == 429) {
                circuitBreaker.onFailure("429");
            } else {
                circuitBreaker.onFailure(String.valueOf(e.getStatusCode().value()));
            }
            throw new ApiException("KIS request failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            circuitBreaker.onFailure("rest");
            throw new ApiException("KIS request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 캐시만 비움. 다음 getAccessToken() 호출 시 KIS에 재발급 요청. 매일 08:00 KST 스케줄용. */
    public void invalidateAccessTokenCache() {
        cachedAccessToken.set(null);
        tokenExpiresAtMillis = 0L;
    }

    public void revokeAccessToken() {
        if (appkey.isBlank() || appsecret.isBlank()) {
            return;
        }
        String tokenToRevoke = cachedAccessToken.get();
        cachedAccessToken.set(null);
        tokenExpiresAtMillis = 0L;
        if (tokenToRevoke == null || tokenToRevoke.isBlank()) {
            return;
        }
        String url = getBaseUrl() + TOKEN_REVOKE_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        Map<String, String> body = Map.of(
                "appkey", appkey,
                "appsecret", appsecret,
                "token", tokenToRevoke
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        try {
            restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException ignored) {
        }
    }

    private int parseTokenExpiresIn(Map<String, Object> res) {
        Object expiresInObj = res.get("expires_in");
        if (expiresInObj instanceof Number) {
            return ((Number) expiresInObj).intValue();
        }
        String expiredStr = getString(res, "access_token_token_expired", null);
        if (expiredStr != null && !expiredStr.isBlank()) {
            try {
                java.time.LocalDateTime expired = java.time.LocalDateTime.parse(
                        expiredStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                long seconds = java.time.Duration.between(java.time.LocalDateTime.now(), expired).getSeconds();
                return seconds > 0 ? (int) seconds : 86400;
            } catch (Exception ignored) {
            }
        }
        return 86400;
    }

    private static String getString(Map<String, Object> m, String key, String defaultValue) {
        if (m == null) return defaultValue;
        Object v = m.get(key);
        if (v == null) return defaultValue;
        String s = v.toString();
        return s != null ? s.trim() : defaultValue;
    }
}
