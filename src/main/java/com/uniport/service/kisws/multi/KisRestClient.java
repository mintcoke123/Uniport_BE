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

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 키 1개당 REST 클라이언트. access token / approval_key 캐시·락·회로차단·레이트리밋.
 */
public class KisRestClient {

    private static final String APPROVAL_PATH = "/oauth2/Approval";
    private static final long APPROVAL_KEY_TTL_MILLIS = 23L * 60 * 60 * 1000;
    private static final long APPROVAL_KEY_REFRESH_BUFFER_MILLIS = 5L * 60 * 1000;

    private final String keyId;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String baseUrlMock;
    private final boolean useMock;
    private final String appkey;
    private final String appsecret;
    private final String accessToken;
    private final KeyCircuitBreaker circuitBreaker;
    private final TokenBucketLimiter restLimiter;

    private volatile String cachedApprovalKey;
    private volatile long approvalKeyExpiresAtMillis = 0L;
    private final ReentrantLock approvalKeyLock = new ReentrantLock();

    public KisRestClient(String keyId, RestTemplate restTemplate,
                         String baseUrl, String baseUrlMock, boolean useMock,
                         String appkey, String appsecret, String accessToken,
                         KeyCircuitBreaker circuitBreaker, TokenBucketLimiter restLimiter) {
        this.keyId = keyId;
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl != null ? baseUrl : "https://openapi.koreainvestment.com:9443";
        this.baseUrlMock = baseUrlMock != null ? baseUrlMock : "https://openapivts.koreainvestment.com:29443";
        this.useMock = useMock;
        this.appkey = appkey != null ? appkey.trim() : "";
        this.appsecret = appsecret != null ? appsecret.trim() : "";
        this.accessToken = accessToken != null ? accessToken.trim() : "";
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
        return !appkey.isBlank() && !appsecret.isBlank() && !accessToken.isBlank();
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
        if (accessToken.isBlank()) {
            throw new ApiException("KIS API access token not configured keyId=" + keyId, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return accessToken;
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

    private static String getString(Map<String, Object> m, String key, String defaultValue) {
        if (m == null) return defaultValue;
        Object v = m.get(key);
        if (v == null) return defaultValue;
        String s = v.toString();
        return s != null ? s.trim() : defaultValue;
    }
}
