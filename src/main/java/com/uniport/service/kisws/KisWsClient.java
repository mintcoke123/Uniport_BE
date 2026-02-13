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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * KIS 실시간 WebSocket 연결 (Java 표준 java.net.http.WebSocket).
 * onOpen 시 H0STCNT0(005930) 구독 JSON 전송. 수신 메시지는 raw 로그.
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
            URI uri = URI.create(base);

            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(uri, new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("KIS WS connect success");
                            String escaped = approvalKey.replace("\\", "\\\\").replace("\"", "\\\"");
                            String subscribeJson = "{\"header\":{\"approval_key\":\"" + escaped
                                    + "\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"utf-8\"}"
                                    + ",\"body\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"005930\"}}";
                            webSocket.sendText(subscribeJson, true);
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            log.info("KIS WS recv: {}", data);
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
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
