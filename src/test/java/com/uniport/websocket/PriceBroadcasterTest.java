package com.uniport.websocket;

import com.uniport.service.VirtualStockService;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceBroadcasterTest {

    @Test
    void subscribe_virtualStockDoesNotRequestKisSubscription() throws Exception {
        KisWsSubscriptionManager subscriptionManager = mock(KisWsSubscriptionManager.class);
        PriceBroadcaster broadcaster = new PriceBroadcaster(subscriptionManager, new VirtualStockService());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");

        try {
            broadcaster.subscribe(session, Set.of("999999"));
            TimeUnit.MILLISECONDS.sleep(500);

            verify(subscriptionManager, never()).ensureSubscribed("999999");
        } finally {
            broadcaster.shutdown();
        }
    }
}
