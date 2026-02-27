package com.uniport.service.kisws.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 주기적 키 헬스 체크. DOWN 키 감지 시 재분배 수행.
 * 장중(09:00~15:30 KST): 5초 간격. 야간: 20초 간격 + 재분배 throttle 5분.
 */
@Component
public class KeyPoolHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeyPoolHealthScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 장중: 09:00 ~ 15:30 KST */
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final KeyPool keyPool;

    /** 키별 마지막 재분배 시각 (throttle). */
    private final ConcurrentHashMap<String, Long> lastRedistributeAtMillis = new ConcurrentHashMap<>();

    /** 야간 시 헬스체크 로직 수행 간격 20초. lastRunAtMillis 기반 가드. */
    private volatile long lastRunAtMillis = 0L;
    private static final long OFF_HOURS_RUN_INTERVAL_MS = 20_000L;

    /** 장중 재분배 최소 간격 1분, 야간 5분 */
    private static final long REDISTRIBUTE_THROTTLE_MARKET_MS = 60_000L;
    private static final long REDISTRIBUTE_THROTTLE_OFF_HOURS_MS = 5 * 60_000L;

    public KeyPoolHealthScheduler(KeyPool keyPool) {
        this.keyPool = keyPool;
    }

    /** 장중 5초마다 수행. 야간은 fixedDelay=5000 유지하되 내부 로직은 20초마다만 수행. */
    @Scheduled(fixedDelay = 5000)
    public void healthCheck() {
        long now = System.currentTimeMillis();
        boolean marketHours = isMarketHours();
        if (!marketHours) {
            if (lastRunAtMillis != 0L && (now - lastRunAtMillis) < OFF_HOURS_RUN_INTERVAL_MS) {
                return;
            }
        }
        lastRunAtMillis = now;

        List<KeyContext> contexts = keyPool.getContexts();
        if (contexts == null || contexts.isEmpty()) {
            return;
        }
        for (KeyContext ctx : contexts) {
            if (ctx.getHealth() != KeyHealth.DOWN) {
                continue;
            }
            String keyId = ctx.getKeyId();
            long throttleMs = marketHours ? REDISTRIBUTE_THROTTLE_MARKET_MS : REDISTRIBUTE_THROTTLE_OFF_HOURS_MS;
            Long last = lastRedistributeAtMillis.get(keyId);
            if (last != null && (now - last) < throttleMs) {
                continue;
            }
            lastRedistributeAtMillis.put(keyId, now);
            keyPool.redistributeFrom(keyId, "scheduled health check");
            log.warn("KIS KeyPool redistributed from DOWN key keyId={}", keyId);
        }
    }

    private static boolean isMarketHours() {
        LocalTime now = LocalTime.now(KST);
        return !now.isBefore(MARKET_OPEN) && now.isBefore(MARKET_CLOSE);
    }
}
