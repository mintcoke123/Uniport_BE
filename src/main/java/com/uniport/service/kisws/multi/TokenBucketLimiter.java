package com.uniport.service.kisws.multi;

/**
 * 키별 토큰 버킷. REST/WS 공통 레이트 제한용.
 * Step2: 단순 synchronized 기반 구현.
 */
public class TokenBucketLimiter {

    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillAtMillis;

    public TokenBucketLimiter(int capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond > 0 ? refillPerSecond : 1.0;
        this.tokens = capacity;
        this.lastRefillAtMillis = System.currentTimeMillis();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Step2에서는 사용하지 않음. 골격만 제공.
     */
    public void acquireBlocking(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillAtMillis;
        if (elapsed <= 0) {
            return;
        }
        lastRefillAtMillis = now;
        double add = elapsed / 1000.0 * refillPerSecond;
        tokens = Math.min(capacity, tokens + add);
    }
}
