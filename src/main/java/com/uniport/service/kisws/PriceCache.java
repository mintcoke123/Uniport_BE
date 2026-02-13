package com.uniport.service.kisws;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시세 in-memory 캐시. key=stockCode, value=PriceSnapshot. thread-safe.
 */
@Component
public class PriceCache {

    private final ConcurrentHashMap<String, PriceSnapshot> cache = new ConcurrentHashMap<>();

    public Optional<PriceSnapshot> get(String stockCode) {
        return Optional.ofNullable(cache.get(stockCode));
    }

    public void put(String stockCode, PriceSnapshot snapshot) {
        if (stockCode != null && snapshot != null) {
            cache.put(stockCode, snapshot);
        }
    }
}
