package com.uniport.service.kisws;

import com.uniport.service.VirtualStockService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KisWsSubscriptionManagerTest {

    @Test
    void ensureSubscribed_virtualStockDoesNotForwardToKisWebSocketClient() {
        KisWsClient kisWsClient = mock(KisWsClient.class);
        KisWsSubscriptionManager manager =
                new KisWsSubscriptionManager(kisWsClient, new VirtualStockService());

        manager.ensureSubscribed("999999");
        manager.ensureSubscribed("999998");

        verify(kisWsClient, never()).sendSubscribe("999999");
        verify(kisWsClient, never()).sendSubscribe("999998");
    }
}
