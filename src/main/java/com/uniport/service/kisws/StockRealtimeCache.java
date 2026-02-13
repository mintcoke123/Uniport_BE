package com.uniport.service.kisws;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KIS 실시간 체결 데이터 메모리 캐시.
 */
@Component
public class StockRealtimeCache {

    private final ConcurrentHashMap<String, RealtimeStock> cache = new ConcurrentHashMap<>();

    public void put(String stockCode, RealtimeStock v) {
        if (stockCode != null && v != null) {
            cache.put(stockCode, v);
        }
    }

    public Optional<RealtimeStock> get(String stockCode) {
        return Optional.ofNullable(cache.get(stockCode));
    }

    /** 현재 캐시 전체 스냅샷 (새 Map 반환). */
    public Map<String, RealtimeStock> getAllSnapshot() {
        return new ConcurrentHashMap<>(cache);
    }
}
