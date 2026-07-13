package com.uniport.service.kisws.multi;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class KisApprovalKeyServiceTest {

    @Test
    void isKeyConfigured_rejectsSecondaryKeyInSingleKeyMode() {
        KisApprovalKeyService service = new KisApprovalKeyService(
                mock(RestTemplate.class),
                "https://kis.example",
                "https://kis-mock.example",
                false,
                "default-appkey",
                "default-appsecret"
        );

        assertFalse(service.isKeyConfigured("secondary"));
    }
}
