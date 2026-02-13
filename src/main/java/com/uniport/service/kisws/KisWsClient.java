package com.uniport.service.kisws;

import com.uniport.service.KisApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * KIS 실시간 WebSocket 연결 (Java 표준 java.net.http.WebSocket).
 * onOpen/onClose/onError 로그만. 구독·파싱·브로드캐스트 없음.
 */
@Component
public class KisWsClient {

    private static final Logger log = LoggerFactory.getLogger(KisWsClient.class);

    @Value("${kis.api.use-mock:false}")
    private boolean useMock;

    private final KisApiService kisApiService;

    public KisWsClient(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    @PostConstruct
    public void connect() {
        log.info("KIS WS connect start");
        if (!kisApiService.isKisConfigured()) {
            log.debug("KIS not configured, skipping WebSocket");
            return;
        }
        try {
            String approvalKey = kisApiService.getWebSocketApprovalKey();
            String base = useMock ? "ws://ops.koreainvestment.com:31000" : "ws://ops.koreainvestment.com:21000";
            String uriStr = base + "?approval_key=" + java.net.URLEncoder.encode(approvalKey, StandardCharsets.UTF_8);
            URI uri = URI.create(uriStr);

            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(uri, new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("KIS WS connect success");
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            log.info("KIS WS close");
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.warn("KIS WS error");
                        }
                    });
        } catch (Exception e) {
            log.warn("KIS WS error");
        }
    }
}
