package com.uniport.controller;

import com.uniport.service.KisApiService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ConfigControllerTest {

    @Test
    void revokeKisToken_rejectsRevocationForExternallyManagedToken() {
        KisApiService kisApiService = mock(KisApiService.class);
        ConfigController controller = new ConfigController(kisApiService, mock(Environment.class));

        ResponseEntity<Map<String, Object>> response = controller.revokeKisToken();

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals(false, response.getBody().get("success"));
        verifyNoInteractions(kisApiService);
    }
}
