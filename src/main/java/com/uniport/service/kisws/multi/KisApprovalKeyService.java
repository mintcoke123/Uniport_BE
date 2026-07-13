package com.uniport.service.kisws.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 키별 approval_key 발급 및 캐싱. ApprovalKeyProvider 구현.
 * TTL 23시간, 만료 5분 전 refresh. 키별 ReentrantLock.
 * KisApiService는 수정하지 않음.
 */
@Service
public class KisApprovalKeyService implements ApprovalKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(KisApprovalKeyService.class);
    private static final String APPROVAL_PATH = "/oauth2/Approval";
    private static final long APPROVAL_KEY_TTL_MILLIS = 23L * 60 * 60 * 1000;
    private static final long APPROVAL_KEY_REFRESH_BUFFER_MILLIS = 5L * 60 * 1000;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String baseUrlMock;
    private final boolean useMock;

    private final ConcurrentHashMap<String, CachedApproval> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 단일 키 폴백: kis.keys 비어 있을 때 kis.api 사용 (keyId "default") */
    private final String fallbackAppkey;
    private final String fallbackAppsecret;

    public KisApprovalKeyService(RestTemplate restTemplate,
                                 @Value("${kis.api.base-url:https://openapi.koreainvestment.com:9443}") String baseUrl,
                                 @Value("${kis.api.base-url-mock:https://openapivts.koreainvestment.com:29443}") String baseUrlMock,
                                 @Value("${kis.api.use-mock:false}") boolean useMock,
                                 @Value("${kis.api.appkey:}") String fallbackAppkey,
                                 @Value("${kis.api.appsecret:}") String fallbackAppsecret) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl != null ? baseUrl : "https://openapi.koreainvestment.com:9443";
        this.baseUrlMock = baseUrlMock != null ? baseUrlMock : "https://openapivts.koreainvestment.com:29443";
        this.useMock = useMock;
        this.fallbackAppkey = fallbackAppkey != null ? fallbackAppkey.trim() : "";
        this.fallbackAppsecret = fallbackAppsecret != null ? fallbackAppsecret.trim() : "";
    }

    private String getBaseUrl() {
        return useMock ? baseUrlMock : baseUrl;
    }

    @Override
    public boolean isKeyConfigured(String keyId) {
        return "default".equals(keyId) && !fallbackAppkey.isBlank() && !fallbackAppsecret.isBlank();
    }

    @Override
    public String getApprovalKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            keyId = "default";
        }
        if (!"default".equals(keyId)) {
            throw new IllegalArgumentException("Only the default KIS key is supported");
        }
        if (fallbackAppkey.isBlank() || fallbackAppsecret.isBlank()) {
            throw new IllegalStateException("KIS default key not configured");
        }
        long now = System.currentTimeMillis();
        CachedApproval cached = cache.get(keyId);
        if (cached != null && cached.approvalKey != null && !cached.approvalKey.isBlank()
                && now < (cached.expiresAtMillis - APPROVAL_KEY_REFRESH_BUFFER_MILLIS)) {
            return cached.approvalKey;
        }
        ReentrantLock lock = locks.computeIfAbsent(keyId, k -> new ReentrantLock());
        lock.lock();
        try {
            cached = cache.get(keyId);
            now = System.currentTimeMillis();
            if (cached != null && cached.approvalKey != null && !cached.approvalKey.isBlank()
                    && now < (cached.expiresAtMillis - APPROVAL_KEY_REFRESH_BUFFER_MILLIS)) {
                return cached.approvalKey;
            }
            String url = getBaseUrl() + APPROVAL_PATH;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            Map<String, String> body = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", fallbackAppkey,
                    "secretkey", fallbackAppsecret
            );
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() == null) {
                throw new IllegalStateException("KIS approval response body is null");
            }
            Map<String, Object> res = response.getBody();
            String approvalKey = getString(res, "approval_key", null);
            if (approvalKey == null || approvalKey.isBlank()) {
                approvalKey = getString(res, "approvalKey", null);
            }
            if (approvalKey == null || approvalKey.isBlank()) {
                throw new IllegalStateException("KIS 실시간 접속키 발급 실패. keyId=" + keyId);
            }
            cache.put(keyId, new CachedApproval(approvalKey, System.currentTimeMillis() + APPROVAL_KEY_TTL_MILLIS));
            return approvalKey;
        } catch (RestClientException e) {
            log.warn("KIS approval request failed keyId={}: {}", keyId, e.getMessage());
            throw new IllegalStateException("KIS approval request failed: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private static String getString(Map<String, Object> m, String key, String defaultValue) {
        if (m == null) return defaultValue;
        Object v = m.get(key);
        if (v == null) return defaultValue;
        String s = v.toString();
        return s != null ? s.trim() : defaultValue;
    }

    private static final class CachedApproval {
        final String approvalKey;
        final long expiresAtMillis;

        CachedApproval(String approvalKey, long expiresAtMillis) {
            this.approvalKey = approvalKey;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
