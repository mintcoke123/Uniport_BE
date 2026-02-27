package com.uniport.service.kisws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * stockCode를 받아서 WS에 subscribe 보장.
 * subscribed/pending은 KeyContext 내부에서 관리. ensureSubscribed는 KisWsClient(sendSubscribe)에 위임.
 */
@Component
public class KisWsSubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(KisWsSubscriptionManager.class);

    private final KisWsClient kisWsClient;

    public KisWsSubscriptionManager(KisWsClient kisWsClient) {
        this.kisWsClient = kisWsClient;
    }

    /**
     * 해당 종목 구독 보장. 정규화(6자리 패딩)는 여기서만 수행 후 KeyPool 경유로 라우팅.
     * KisWsClient.sendSubscribe(normalized) → KeyPool.ensureSubscribed(normalized) → KeyContext.
     * 동일 normalize 결과만 사용해 중복 subscribe 방지.
     */
    public void ensureSubscribed(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        String normalized = normalizeStockCode(stockCode.trim());
        kisWsClient.sendSubscribe(normalized);
        log.debug("KIS WS subscribe requested: {}", normalized);
    }

    /**
     * 해당 종목 구독 해제. 지정가 체결 등으로 더 이상 필요 없을 때 호출하면 KIS 슬롯이 반환되어
     * 다른 종목 구독(69→70 변동) 시 정상 분산 가능. stockCode는 6자리 정규화 후 해제.
     */
    public void removeSubscription(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        String normalized = normalizeStockCode(stockCode.trim());
        kisWsClient.removeSubscription(normalized);
        log.debug("KIS WS unsubscribe requested: {}", normalized);
    }

    /** WS 연결됐을 때 호출. KeyContext 내부에서 drain 처리하므로 빈 메서드. */
    public void onWsConnected() {
    }

    /** 연결 종료 시 호출. KeyContext 내부에서 subscribed→pending 처리하므로 빈 메서드. */
    public void onWsDisconnected() {
    }

    /** 연결 종료 시 호출. 재연결 후 재구독 가능하도록 set 비움. (외부에서 전체 초기화 필요 시 사용) */
    public void clearSubscribedCodes() {
        // KeyContext별 구독 상태는 KeyPool/KeyContext 내부. 외부 API 유지용 no-op.
    }

    private static String normalizeStockCode(String code) {
        if (code == null || code.length() >= 6) {
            return code;
        }
        return String.format("%6s", code).replace(' ', '0');
    }
}
