package com.uniport.service.kisws;

import com.uniport.service.kisws.multi.KeyPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * KIS 실시간 WebSocket 멀티키 파사드.
 * 실제 연결/구독은 KeyPool 및 KeyContext에서 처리.
 */
@Component
public class KisWsClient {

    private static final Logger log = LoggerFactory.getLogger(KisWsClient.class);

    private final KeyPool keyPool;

    public KisWsClient(KeyPool keyPool) {
        this.keyPool = keyPool;
    }

    /**
     * WS 연결 시작은 KeyPool.init()(@PostConstruct)에서 KeyContext.connect()로 이미 실행됨.
     * 호출 시 로그만 남기고 종료. 앱 시작 시 반드시 KeyContext.connect()가 실행되도록 KeyPool에 위임.
     */
    public void connect() {
        log.info("KIS WS connect: KeyPool has already started connections at startup (KeyContext.connect() in KeyPool.init())");
    }

    /** 연결 여부. 구독 요청은 연결된 경우에만 유효. */
    public boolean isConnected() {
        return keyPool.anyConnected();
    }

    /** 강제 재연결 (예: 매일 07:59:50 KST). */
    public void forceReconnect(String reason) {
        log.info("KIS WS force reconnect: {}", reason);
        keyPool.forceReconnectAll(reason);
    }

    /** H0STCNT0 구독. SymbolRouter로 배분된 KeyContext에 위임. */
    public void sendSubscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        keyPool.ensureSubscribed(stockCode);
    }

    /** 구독 해제. 지정가 체결 등으로 69→70 변동 시 슬롯 반환. stockCode는 정규화된 값. */
    public void removeSubscription(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        keyPool.removeSubscription(stockCode);
    }

    @PreDestroy
    public void shutdown() {
        keyPool.shutdown();
    }
}
