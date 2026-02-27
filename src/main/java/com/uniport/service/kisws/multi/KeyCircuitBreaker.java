package com.uniport.service.kisws.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 키별 회로 차단기. 연속 실패 시 일정 시간 DOWN 처리.
 */
public class KeyCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(KeyCircuitBreaker.class);

    private final String keyId;
    private final int failureThreshold;
    private final long coolDownMillis;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long downUntilMillis = 0L;

    public KeyCircuitBreaker(String keyId) {
        this(keyId, 3, 30_000L);
    }

    public KeyCircuitBreaker(String keyId, int failureThreshold, long coolDownMillis) {
        this.keyId = keyId;
        this.failureThreshold = failureThreshold;
        this.coolDownMillis = coolDownMillis;
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now >= downUntilMillis) {
            return true;
        }
        return false;
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
    }

    public void onFailure(String reason) {
        int n = consecutiveFailures.incrementAndGet();
        if (n >= failureThreshold) {
            long now = System.currentTimeMillis();
            downUntilMillis = now + coolDownMillis;
            log.warn("KIS key circuit open keyId={} reason={} coolDownMs={}", keyId, reason, coolDownMillis);
        }
    }

    public KeyHealth health() {
        long now = System.currentTimeMillis();
        if (now < downUntilMillis) {
            return KeyHealth.DOWN;
        }
        int f = consecutiveFailures.get();
        if (f == 0) {
            return KeyHealth.HEALTHY;
        }
        return KeyHealth.DEGRADED;
    }
}
