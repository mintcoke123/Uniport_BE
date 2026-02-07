package com.uniport.service;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.dto.MarketIndexDTO;
import com.uniport.dto.OrderResponseDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.OrderStatus;
import com.uniport.entity.OrderType;
import com.uniport.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KIS(한국투자증권) Open API 연동 서비스.
 * 토큰 발급, 주식 현재가, 거래량 순위 조회 및 모의투자용 주문 스텁을 담당합니다.
 * 주문은 실제 API를 호출하지 않고 스텁만 반환합니다(실제 돈 이동 없음).
 * appkey/appsecret이 설정되지 않으면 시세·거래량도 스텁으로 동작합니다.
 */
@Service
public class KisApiService {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String TOKEN_REVOKE_PATH = "/oauth2/revokeP";
    /** 실시간(웹소켓) 접속키 발급 */
    private static final String APPROVAL_PATH = "/oauth2/Approval";
    private static final String STOCK_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String VOLUME_RANK_PATH = "/uapi/domestic-stock/v1/quotations/volume-rank";
    /** 상위 랭킹 (등락률 순위) GET /uapi/domestic-stock/v1/ranking/fluctuation */
    private static final String FLUCTUATION_RANK_PATH = "/uapi/domestic-stock/v1/ranking/fluctuation";
    private static final String INDEX_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-index-price";
    /** 지수 일/주/월/년 차트 시세 */
    private static final String INDEX_CHART_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice";
    private static final String TR_ID_STOCK_PRICE = "FHKST01010100";
    /** 거래량 순위 조회 전용 tr_id (현재가 조회 FHKST01010100과 구분) */
    private static final String TR_ID_VOLUME_RANK = "FHPST01710000";
    /** fluctuation(상위 랭킹 등락률 순) tr_id */
    private static final String TR_ID_FLUCTUATION_RANK = "FHPST01700000";
    private static final String TR_ID_INDEX_PRICE = "FHPUP02100000";
    /** 지수 차트 시세 tr_id */
    private static final String TR_ID_INDEX_CHART = "FHKUP03500100";
    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 60;
    /** KIS 미설정 시 전역 예외 처리에서 503 + code/message/configured 응답에 사용 */
    public static final String ERROR_CODE_KIS_NOT_CONFIGURED = "KIS_NOT_CONFIGURED";

    private final RestTemplate restTemplate;

    @Value("${kis.api.base-url:https://openapi.koreainvestment.com:9443}")
    private String baseUrl;
    @Value("${kis.api.base-url-mock:https://openapivts.koreainvestment.com:29443}")
    private String baseUrlMock;
    @Value("${kis.api.appkey:}")
    private String appkey;
    @Value("${kis.api.appsecret:}")
    private String appsecret;
    @Value("${kis.api.use-mock:false}")
    private boolean useMock;

    private final AtomicReference<String> cachedAccessToken = new AtomicReference<>();
    private volatile long tokenExpiresAtMillis = 0L;
    /** 동시에 한 번만 토큰 발급되도록 락 (배포 시 레이트리밋/지연 방지) */
    private final ReentrantLock tokenIssueLock = new ReentrantLock();

    public KisApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String getBaseUrl() {
        return useMock ? baseUrlMock : baseUrl;
    }

    private boolean isConfigured() {
        return appkey != null && !appkey.isBlank() && appsecret != null && !appsecret.isBlank();
    }

    /** KIS appkey/appsecret 설정 여부. GET /api/config/kis-status 등에서 사용. */
    public boolean isKisConfigured() {
        return isConfigured();
    }

    /**
     * KIS OAuth2 접근토큰 발급. POST /oauth2/tokenP (인증-001).
     * access_token + 만료시간 메모리 캐시, 만료 60초 전까지 재사용.
     * 동시 요청 시 락으로 1회만 발급(배포 시 레이트리밋/지연 방지).
     */
    public String getAccessToken() {
        String key = appkey != null ? appkey.trim() : "";
        String secret = appsecret != null ? appsecret.trim() : "";
        if (key.isBlank() || secret.isBlank()) {
            throw new ApiException("KIS API appkey/appsecret not configured", HttpStatus.INTERNAL_SERVER_ERROR);
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
                    "appkey", key,
                    "appsecret", secret
            );
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() == null) {
                throw new ApiException("KIS token response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            Map<String, Object> res = response.getBody();
            String accessToken = getString(res, "access_token", null);
            if (accessToken == null || accessToken.isBlank()) {
                accessToken = getString(res, "accessToken", null);
            }
            if (accessToken == null || accessToken.isBlank()) {
                String kisError = kisErrorMessage(res, "token");
                throw new ApiException("KIS 접근토큰 발급 실패. " + kisError, HttpStatus.SERVICE_UNAVAILABLE);
            }
            int expiresInSeconds = parseTokenExpiresIn(res);
            cachedAccessToken.set(accessToken);
            tokenExpiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000L;
            return accessToken;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String bodyStr = e.getResponseBodyAsString();
            throw new ApiException("KIS token request failed: " + e.getStatusCode() + " " + (bodyStr != null ? bodyStr : e.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            throw new ApiException("KIS token request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            tokenIssueLock.unlock();
        }
    }

    /** KIS 토큰 응답에서 만료 시간(초) 추출. expires_in 또는 access_token_token_expired(날짜 문자열) 지원 */
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

    /**
     * KIS 접근토큰 폐기. POST /oauth2/revokeP, body JSON { appkey, appsecret, token }.
     * 폐기 후 캐시된 토큰을 비우며, 다음 API 호출 시 새 토큰을 발급받습니다.
     */
    public void revokeAccessToken() {
        String key = appkey != null ? appkey.trim() : "";
        String secret = appsecret != null ? appsecret.trim() : "";
        if (key.isBlank() || secret.isBlank()) {
            throw new ApiException("KIS API appkey/appsecret not configured", HttpStatus.INTERNAL_SERVER_ERROR);
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
                "appkey", key,
                "appsecret", secret,
                "token", tokenToRevoke
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        try {
            restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            // 폐기 요청 실패해도 캐시는 이미 비워둠. 다음 발급 시 새 토큰 사용.
        }
    }

    /**
     * 실시간(웹소켓) 접속키 발급. POST /oauth2/Approval, body JSON { grant_type, appkey, secretkey }.
     * KIS 명세: secretkey 필드에 appsecret 값 전달. 응답의 approval_key를 웹소켓 연결 시 사용.
     */
    public String getWebSocketApprovalKey() {
        String key = appkey != null ? appkey.trim() : "";
        String secret = appsecret != null ? appsecret.trim() : "";
        if (key.isBlank() || secret.isBlank()) {
            throw new ApiException("KIS API appkey/appsecret not configured", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String url = getBaseUrl() + APPROVAL_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", key,
                "secretkey", secret
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() == null) {
                throw new ApiException("KIS approval response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            Map<String, Object> res = response.getBody();
            String approvalKey = getString(res, "approval_key", null);
            if (approvalKey == null || approvalKey.isBlank()) {
                approvalKey = getString(res, "approvalKey", null);
            }
            if (approvalKey == null || approvalKey.isBlank()) {
                throw new ApiException("KIS 실시간 접속키 발급 실패. " + kisErrorMessage(res, "approval"), HttpStatus.SERVICE_UNAVAILABLE);
            }
            return approvalKey;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException("KIS approval request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private HttpHeaders buildAuthHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + getAccessToken());
        headers.set("appkey", appkey != null ? appkey.trim() : "");
        headers.set("appsecret", appsecret != null ? appsecret.trim() : "");
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
        return headers;
    }

    /**
     * 주식 현재가 조회. KIS domestic-stock inquire-price API 호출.
     * appkey/appsecret 미설정 시 예외 발생(스텁 반환 금지).
     */
    public StockPriceDTO getStockPrice(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new ApiException("Stock code is required", HttpStatus.BAD_REQUEST);
        }
        if (!isConfigured()) {
            throw new ApiException("KIS API가 설정되지 않았습니다.", HttpStatus.SERVICE_UNAVAILABLE, ERROR_CODE_KIS_NOT_CONFIGURED);
        }
        String url = UriComponentsBuilder.fromUriString(getBaseUrl() + STOCK_PRICE_PATH)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode.trim())
                .build()
                .toUriString();
        HttpHeaders headers = buildAuthHeaders(TR_ID_STOCK_PRICE);
        headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ApiException("KIS stock price response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            String rtCd = (String) body.get("rt_cd");
            if (rtCd != null && !"0".equals(rtCd)) {
                throw new ApiException(kisErrorMessage(body, "stock price"), HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> outputMap = getStockPriceOutputMap(body);
            if (outputMap == null) {
                throw new ApiException("KIS stock price output2 is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            return mapToStockPriceDTO(stockCode.trim(), outputMap);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException("KIS stock price request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStockPriceOutputMap(Map<String, Object> body) {
        Map<String, Object> out = (Map<String, Object>) body.get("output2");
        if (out == null) out = (Map<String, Object>) body.get("output");
        if (out == null) out = (Map<String, Object>) body.get("Output");
        return out;
    }

    /** 상품유형 약어는 종목명으로 쓰지 않음 (API가 prdt_name 등으로 반환하는 경우) */
    private static final java.util.Set<String> PRODUCT_TYPE_ABBREVS = java.util.Set.of("ETF", "ELW", "ETN");

    /** KIS 응답 output에서 종목명 추출. 여러 키 시도 후 유효하지 않으면 "종목_" + code 반환. */
    private String getStockNameFromOutput(Map<String, Object> output, String stockCode) {
        String[] nameKeys = {"hts_kor_isnm", "itms_nm", "prdt_name", "kor_isnm", "stock_name", "stck_shrn_iscd"};
        for (String key : nameKeys) {
            String v = getString(output, key, null);
            if (v != null && !v.isBlank()) {
                String trimmed = v.trim();
                if (!trimmed.equals(stockCode) && !trimmed.matches("\\d{6}")
                        && !PRODUCT_TYPE_ABBREVS.contains(trimmed)) {
                    return trimmed;
                }
            }
        }
        return "종목_" + stockCode;
    }

    private StockPriceDTO mapToStockPriceDTO(String stockCode, Map<String, Object> output2) {
        String stockName = getStockNameFromOutput(output2, stockCode);
        BigDecimal currentPrice = getBigDecimal(output2, "stck_prpr");
        BigDecimal changeAmount = getBigDecimal(output2, "prdy_vrss");
        BigDecimal changeRate = getBigDecimal(output2, "prdy_ctrt");
        Long volume = getLong(output2, "acml_vol");
        if (currentPrice == null) {
            currentPrice = BigDecimal.ZERO;
        }
        if (changeAmount == null) {
            changeAmount = BigDecimal.ZERO;
        }
        if (changeRate == null) {
            changeRate = BigDecimal.ZERO;
        }
        if (volume == null) {
            volume = 0L;
        }
        return StockPriceDTO.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(currentPrice)
                .changeAmount(changeAmount)
                .changeRate(changeRate)
                .volume(volume)
                .build();
    }

    private static String getString(Map<String, Object> m, String key, String defaultValue) {
        Object v = m.get(key);
        if (v == null) return defaultValue;
        return String.valueOf(v).trim();
    }

    private static BigDecimal getBigDecimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try {
            return new BigDecimal(String.valueOf(v).trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long getLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static String kisErrorMessage(Map<String, Object> body, String context) {
        if (body == null) {
            return "KIS API error (" + (context != null ? context : "") + ") response body is null";
        }
        Object rtCdVal = body.get("rt_cd") != null ? body.get("rt_cd") : body.get("rtCd");
        Object msgCdVal = body.get("msg_cd") != null ? body.get("msg_cd") : body.get("msgCd");
        Object msg1Val = body.get("msg1") != null ? body.get("msg1") : body.get("message");
        Object errVal = body.get("error");
        Object errDescVal = body.get("error_description");
        String rtCd = rtCdVal != null ? String.valueOf(rtCdVal).trim() : "";
        String msgCd = msgCdVal != null ? String.valueOf(msgCdVal).trim() : "";
        String msg1 = msg1Val != null ? String.valueOf(msg1Val).trim() : "";
        String err = errVal != null ? String.valueOf(errVal).trim() : "";
        String errDesc = errDescVal != null ? String.valueOf(errDescVal).trim() : "";
        StringBuilder sb = new StringBuilder("KIS API error (").append(context != null ? context : "").append(")");
        if (!rtCd.isEmpty() || !msgCd.isEmpty() || !msg1.isEmpty()) {
            sb.append(" rt_cd=").append(rtCd.isEmpty() ? "(empty)" : rtCd);
            sb.append(" msg_cd=").append(msgCd.isEmpty() ? "(empty)" : msgCd);
            sb.append(" msg1=").append(msg1.isEmpty() ? "(empty)" : msg1);
        }
        if (!err.isEmpty() || !errDesc.isEmpty()) {
            sb.append(" error=").append(err.isEmpty() ? "(empty)" : err);
            sb.append(" error_description=").append(errDesc.isEmpty() ? "(empty)" : errDesc);
        }
        if (sb.length() == ("KIS API error (" + (context != null ? context : "") + ")").length()) {
            sb.append(" (no detail in response)");
        }
        return sb.toString();
    }

    /**
     * 거래량 상위순 조회. KIS 거래량순위[v1_국내주식-047] API 호출.
     * 명세: GET /uapi/domestic-stock/v1/quotations/volume-rank, Query Parameter.
     * appkey/appsecret 미설정 시 예외 발생(스텁 반환 금지).
     */
    @SuppressWarnings("unchecked")
    public List<StockPriceDTO> getVolumeRank() {
        if (!isConfigured()) {
            throw new ApiException("KIS API가 설정되지 않았습니다.", HttpStatus.SERVICE_UNAVAILABLE, ERROR_CODE_KIS_NOT_CONFIGURED);
        }
        String url = UriComponentsBuilder.fromUriString(getBaseUrl() + VOLUME_RANK_PATH)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                .queryParam("FID_INPUT_ISCD", "0000")
                .queryParam("FID_DIV_CLS_CODE", "0")
                .queryParam("FID_BLNG_CLS_CODE", "0")
                .queryParam("FID_TRGT_CLS_CODE", "111111111")
                .queryParam("FID_TRGT_EXLS_CLS_CODE", "0000000000")
                .queryParam("FID_INPUT_PRICE_1", "")
                .queryParam("FID_INPUT_PRICE_2", "")
                .queryParam("FID_VOL_CNT", "")
                .queryParam("FID_INPUT_DATE_1", "")
                .build()
                .toUriString();
        HttpHeaders headers = buildAuthHeaders(TR_ID_VOLUME_RANK);
        headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ApiException("KIS volume rank response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            String rtCd = body.get("rt_cd") != null ? String.valueOf(body.get("rt_cd")).trim() : "";
            if (!rtCd.isEmpty() && !"0".equals(rtCd)) {
                throw new ApiException(kisErrorMessage(body, "volume rank"), HttpStatus.BAD_REQUEST);
            }
            List<Map<String, Object>> outputList = (List<Map<String, Object>>) body.get("output2");
            if (outputList == null) {
                outputList = (List<Map<String, Object>>) body.get("Output");
            }
            if (outputList == null) {
                outputList = (List<Map<String, Object>>) body.get("output");
            }
            List<StockPriceDTO> list = new ArrayList<>();
            if (outputList != null) {
                for (Map<String, Object> item : outputList) {
                    String code = getString(item, "mksc_shrn_iscd", getString(item, "iscd", ""));
                    if (code != null && !code.isBlank()) {
                        list.add(mapToStockPriceDTO(code, item));
                    }
                }
            }
            return list;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException("KIS volume rank request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 상승률순 조회. fluctuation API (fid_rank_sort_cls_code=0, fid_prc_cls_code=1, fid_input_iscd=0001).
     */
    public List<StockPriceDTO> getFluctuationRank() {
        return callFluctuationRank("0", "1", "0001");
    }

    /**
     * 하락율순 조회. fluctuation API (fid_rank_sort_cls_code=1, fid_prc_cls_code=1, fid_input_iscd=0001).
     */
    public List<StockPriceDTO> getFallingRank() {
        return callFluctuationRank("1", "1", "0001");
    }

    /**
     * KIS 상위 랭킹. GET /uapi/domestic-stock/v1/ranking/fluctuation (등락률 순위).
     * fid_input_iscd: 0001=코스피, 1001=코스닥, 0000=전체.
     */
    private List<StockPriceDTO> callFluctuationRank(String fidRankSortClsCode, String fidPrcClsCode, String fidInputIscd) {
        if (!isConfigured()) {
            throw new ApiException("KIS API가 설정되지 않았습니다.", HttpStatus.SERVICE_UNAVAILABLE, ERROR_CODE_KIS_NOT_CONFIGURED);
        }
        String url = UriComponentsBuilder.fromUriString(getBaseUrl() + FLUCTUATION_RANK_PATH)
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_cond_scr_div_code", "20170")
                .queryParam("fid_rank_sort_cls_code", fidRankSortClsCode)
                .queryParam("fid_prc_cls_code", fidPrcClsCode)
                .queryParam("fid_input_iscd", fidInputIscd != null ? fidInputIscd : "0001")
                .queryParam("fid_rsfl_rate1", "")
                .queryParam("fid_rsfl_rate2", "")
                .queryParam("fid_input_price_1", "")
                .queryParam("fid_input_price_2", "")
                .queryParam("fid_vol_cnt", "")
                .queryParam("fid_input_cnt_1", "0")
                .queryParam("fid_trgt_cls_code", "0")
                .queryParam("fid_div_cls_code", "0")
                .queryParam("fid_trgt_exls_cls_code", "0")
                .build()
                .toUriString();
        // tr_id 반드시 랭킹용 FHPST01700000. 코스피/코스닥 지수용(FHPUP02100000)이면 조용히 실패함.
        String trIdRanking = TR_ID_FLUCTUATION_RANK;
        HttpHeaders headers = buildAuthHeaders(trIdRanking);
        headers.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> resBody = response.getBody();
            if (resBody == null) {
                throw new ApiException("KIS fluctuation rank response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            String rtCd = (String) resBody.get("rt_cd");
            if (rtCd != null && !"0".equals(rtCd)) {
                throw new ApiException(kisErrorMessage(resBody, "fluctuation rank"), HttpStatus.BAD_REQUEST);
            }
            Object output = resBody.get("output");
            if (output == null) {
                output = resBody.get("output2");
            }
            List<StockPriceDTO> list = new ArrayList<>();
            if (output instanceof List) {
                for (Object o : (List<?>) output) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> item = (Map<String, Object>) o;
                        String code = getString(item, "stck_shrn_iscd", getString(item, "mksc_shrn_iscd", getString(item, "iscd", "")));
                        if (code != null && !code.isBlank()) {
                            list.add(mapToStockPriceDTO(code, item));
                        }
                    }
                }
            }
            return list;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException("KIS fluctuation rank request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 주식 주문 실행. 모의투자 전용 — 실제 KIS 주문 API를 호출하지 않고 스텁만 반환합니다.
     * (실제 돈이 오가는 로직은 코드베이스에 포함하지 않음)
     */
    public OrderResponseDTO placeOrder(String stockCode, int quantity, BigDecimal price, OrderType type) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new ApiException("Stock code is required", HttpStatus.BAD_REQUEST);
        }
        if (quantity <= 0 || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Invalid order parameters", HttpStatus.BAD_REQUEST);
        }
        return placeOrderStub(stockCode, quantity, price, type);
    }

    private OrderResponseDTO placeOrderStub(String stockCode, int quantity, BigDecimal price, OrderType type) {
        return OrderResponseDTO.builder()
                .orderId(null)
                .stockCode(stockCode)
                .quantity(quantity)
                .price(price)
                .orderType(type)
                .status(OrderStatus.COMPLETED)
                .orderDate(LocalDateTime.now())
                .externalOrderNo("ORD-" + System.currentTimeMillis())
                .message("Stub order accepted")
                .build();
    }

    /**
     * 종목 검색. KIS 거래량 순위 데이터에서 키워드(종목명·종목코드)로 필터링합니다.
     * KIS에 키워드 검색 API가 없어 거래량 순위 목록에서 매칭된 항목을 반환합니다. 설정이 없으면 스텁.
     */
    public List<StockPriceDTO> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String k = keyword.trim().toLowerCase();
        try {
            List<StockPriceDTO> list = getVolumeRank();
            return list.stream()
                    .filter(s -> (s.getStockName() != null && s.getStockName().toLowerCase().contains(k))
                            || (s.getStockCode() != null && s.getStockCode().toLowerCase().contains(k)))
                    .limit(20)
                    .toList();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 코스피/코스닥 지수 조회. KIS 국내업종 현재지수 API 호출.
     * FID_INPUT_ISCD: 0001=코스피, 1001=코스닥. tr_id: FHPUP02100000.
     */
    public MarketIndexDTO getMarketIndex(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            throw new ApiException("Index code is required", HttpStatus.BAD_REQUEST);
        }
        if (!isConfigured()) {
            throw new ApiException("KIS API가 설정되지 않았습니다.", HttpStatus.SERVICE_UNAVAILABLE, ERROR_CODE_KIS_NOT_CONFIGURED);
        }
        String fidInputIscd = toIndexFidInputIscd(indexCode);
        String url = UriComponentsBuilder.fromUriString(getBaseUrl() + INDEX_PRICE_PATH)
                .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                .queryParam("FID_INPUT_ISCD", fidInputIscd)
                .build()
                .toUriString();
        HttpEntity<Void> request = new HttpEntity<>(buildAuthHeaders(TR_ID_INDEX_PRICE));
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ApiException("KIS index price response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            String rtCd = (String) body.get("rt_cd");
            if (rtCd != null && !"0".equals(rtCd)) {
                throw new ApiException(kisErrorMessage(body, "market index"), HttpStatus.BAD_REQUEST);
            }
            Object output = body.get("output");
            if (output == null) {
                return getMarketIndexStub(indexCode);
            }
            Map<String, Object> outputMap = toSingleOutputMap(output);
            if (outputMap == null) {
                return getMarketIndexStub(indexCode);
            }
            return mapToMarketIndexDTO(indexCode, outputMap);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException("KIS index price request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 일/주/월/년 지수 차트 시세 조회. KIS inquire-daily-indexchartprice API.
     * FID_PERIOD_DIV_CODE: D=일봉, W=주봉, M=월봉, Y=년봉. 날짜 형식: yyyyMMdd.
     */
    public List<IndexChartPriceItemDTO> getIndexChartPrice(String indexCode, String startDate, String endDate, String periodDivCode) {
        if (indexCode == null || indexCode.isBlank()) {
            throw new ApiException("Index code is required", HttpStatus.BAD_REQUEST);
        }
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            throw new ApiException("Start date and end date are required (yyyyMMdd)", HttpStatus.BAD_REQUEST);
        }
        String period = periodDivCode != null ? periodDivCode.trim().toUpperCase() : "D";
        if (!period.matches("^[DWMY]$")) {
            throw new ApiException("period must be D(일봉), W(주봉), M(월봉), or Y(년봉)", HttpStatus.BAD_REQUEST);
        }
        if (!isConfigured()) {
            throw new ApiException("KIS API가 설정되지 않았습니다.", HttpStatus.SERVICE_UNAVAILABLE, ERROR_CODE_KIS_NOT_CONFIGURED);
        }
        String fidInputIscd = toIndexFidInputIscd(indexCode);
        String url = getBaseUrl() + INDEX_CHART_PRICE_PATH;
        HttpHeaders headers = buildAuthHeaders(TR_ID_INDEX_CHART);
        headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        Map<String, Object> requestBody = Map.of(
                "FID_COND_MRKT_DIV_CODE", "U",
                "FID_INPUT_ISCD", fidInputIscd,
                "FID_INPUT_DATE_1", startDate.trim(),
                "FID_INPUT_DATE_2", endDate.trim(),
                "FID_PERIOD_DIV_CODE", period
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> resBody = response.getBody();
            if (resBody == null) {
                throw new ApiException("KIS index chart response body is null", HttpStatus.SERVICE_UNAVAILABLE);
            }
            String rtCd = (String) resBody.get("rt_cd");
            if (rtCd != null && !"0".equals(rtCd)) {
                throw new ApiException(kisErrorMessage(resBody, "index chart"), HttpStatus.BAD_REQUEST);
            }
            Object output = resBody.get("output2");
            if (output == null) {
                output = resBody.get("output");
            }
            List<IndexChartPriceItemDTO> list = new ArrayList<>();
            if (output instanceof List) {
                for (Object item : (List<?>) output) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) item;
                        list.add(mapToIndexChartPriceItemDTO(m));
                    }
                }
            }
            return list;
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException("KIS index chart request failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static IndexChartPriceItemDTO mapToIndexChartPriceItemDTO(Map<String, Object> m) {
        String date = getString(m, "stck_bsop_date", getString(m, "stck_std", getString(m, "date", "")));
        BigDecimal open = getBigDecimal(m, "stck_oprc");
        if (open == null) open = getBigDecimal(m, "bstp_nmix_oprc");
        BigDecimal high = getBigDecimal(m, "stck_hgpr");
        if (high == null) high = getBigDecimal(m, "bstp_nmix_hgpr");
        BigDecimal low = getBigDecimal(m, "stck_lwpr");
        if (low == null) low = getBigDecimal(m, "bstp_nmix_lwpr");
        BigDecimal close = getBigDecimal(m, "stck_clpr");
        if (close == null) close = getBigDecimal(m, "bstp_nmix_prpr");
        return IndexChartPriceItemDTO.builder()
                .date(date)
                .open(open != null ? open : BigDecimal.ZERO)
                .high(high != null ? high : BigDecimal.ZERO)
                .low(low != null ? low : BigDecimal.ZERO)
                .close(close != null ? close : BigDecimal.ZERO)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toSingleOutputMap(Object output) {
        if (output instanceof Map) {
            return (Map<String, Object>) output;
        }
        if (output instanceof List && !((List<?>) output).isEmpty()) {
            Object first = ((List<?>) output).get(0);
            if (first instanceof Map) {
                return (Map<String, Object>) first;
            }
        }
        return null;
    }

    /** FID_INPUT_ISCD: 0001=코스피, 1001=코스닥 */
    private static String toIndexFidInputIscd(String indexCode) {
        if (indexCode == null) return "0001";
        String upper = indexCode.trim().toUpperCase();
        if (upper.contains("KOSDAQ") || "1001".equals(indexCode.trim())) return "1001";
        return "0001";
    }

    private MarketIndexDTO mapToMarketIndexDTO(String indexCode, Map<String, Object> output) {
        String name = "1001".equals(toIndexFidInputIscd(indexCode)) ? "KOSDAQ" : "KOSPI";
        BigDecimal value = getBigDecimal(output, "bstp_nmix_prpr");
        BigDecimal changeAmount = getBigDecimal(output, "bstp_nmix_prdy_vrss");
        BigDecimal changeRate = getBigDecimal(output, "bstp_nmix_prdy_ctrt");
        if (value == null) value = BigDecimal.ZERO;
        if (changeAmount == null) changeAmount = BigDecimal.ZERO;
        if (changeRate == null) changeRate = BigDecimal.ZERO;
        return MarketIndexDTO.builder()
                .indexCode(indexCode)
                .indexName(name)
                .value(value)
                .changeAmount(changeAmount)
                .changeRate(changeRate)
                .build();
    }

    private MarketIndexDTO getMarketIndexStub(String indexCode) {
        String name = "KOSPI".equalsIgnoreCase(indexCode != null ? indexCode.trim() : "") ? "KOSPI" : "KOSDAQ";
        BigDecimal value = BigDecimal.valueOf(2500);
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal rate = BigDecimal.ZERO;
        return MarketIndexDTO.builder()
                .indexCode(indexCode)
                .indexName(name)
                .value(value)
                .changeAmount(change)
                .changeRate(rate)
                .build();
    }

    /**
     * 주문 취소 (스텁). 실제 구현 시 KIS 취소 API 호출.
     */
    public void cancelOrder(String accountNo, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new ApiException("Order number is required", HttpStatus.BAD_REQUEST);
        }
    }
}
