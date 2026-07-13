package com.uniport.service.kisws.multi;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class KisRestClientTest {

    @Test
    void getAccessToken_returnsConfiguredTokenWithoutIssuingANewToken() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        KisRestClient client = new KisRestClient(
                "default",
                restTemplate,
                "https://kis.example",
                "https://kis-mock.example",
                false,
                "appkey",
                "appsecret",
                "fixed-access-token",
                new KeyCircuitBreaker("default"),
                new TokenBucketLimiter(20, 10.0)
        );

        String token = client.getAccessToken();

        assertEquals("fixed-access-token", token);
        verifyNoInteractions(restTemplate);
    }
}
