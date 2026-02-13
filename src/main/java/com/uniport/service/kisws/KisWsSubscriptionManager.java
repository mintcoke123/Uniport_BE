package com.uniport.service.kisws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * stockCode를 받아서 WS에 subscribe 보장.
 * 이미 구독 중이면 skip. 연결 전이면 pending에 쌓아두고 연결 시 drain하여 전송.
 * subscribe payload: body.input 포함 {"body":{"input":{"tr_id":"H0STCNT0","tr_key":"005930"}}}.
 */
@Component
public class KisWsSubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(KisWsSubscriptionManager.class);

    private final KisWsClient kisWsClient;
    /** 이미 구독 요청을 보낸 종목코드 */
    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();
    /** WS 미연결 시 대기할 종목코드 (연결되면 drain) */
    private final Set<String> pendingCodes = ConcurrentHashMap.newKeySet();

    public KisWsSubscriptionManager(KisWsClient kisWsClient) {
        this.kisWsClient = kisWsClient;
    }

    /**
     * 해당 종목 구독 보장. 이미 구독 중이면 return.
     * 연결됐으면 바로 전송, 아니면 pending에 넣고 연결 시 drain.
     */
    public void ensureSubscribed(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        String code = normalizeStockCode(stockCode.trim());
        if (subscribedCodes.contains(code)) {
            return;
        }
        if (kisWsClient.isConnected()) {
            kisWsClient.sendSubscribe(code);
            subscribedCodes.add(code);
            log.debug("KIS WS subscribe requested: {}", code);
        } else {
            pendingCodes.add(code);
            log.debug("KIS WS subscribe pending (not connected): {}", code);
        }
    }

    /** WS 연결됐을 때 호출. pending을 비우며 전부 subscribe 전송. */
    public void onWsConnected() {
        if (!kisWsClient.isConnected()) {
            return;
        }
        var toSend = new ArrayList<>(pendingCodes);
        pendingCodes.clear();
        for (String code : toSend) {
            if (subscribedCodes.contains(code)) {
                continue;
            }
            kisWsClient.sendSubscribe(code);
            subscribedCodes.add(code);
            log.debug("KIS WS subscribe drained: {}", code);
        }
    }

    /** 연결 종료 시 호출. 재연결 후 재구독 가능하도록 set 비움. */
    public void clearSubscribedCodes() {
        subscribedCodes.clear();
        pendingCodes.clear();
    }

    private static String normalizeStockCode(String code) {
        if (code == null || code.length() >= 6) {
            return code;
        }
        return String.format("%6s", code).replace(' ', '0');
    }
}
