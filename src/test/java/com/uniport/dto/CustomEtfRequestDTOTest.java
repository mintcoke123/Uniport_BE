package com.uniport.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomEtfRequestDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createRequest_acceptsFrontendLegacyNameAndStocksFields() throws Exception {
        String json = """
                {
                  "name": "나만의 주식 ETF",
                  "description": "검증된 주식으로 구성한 나만의 ETF",
                  "stocks": [
                    {"stockId": "KRX_373220", "weight": 40},
                    {"stockId": "KRX_005930", "weight": 30},
                    {"stockId": "KRX_000660", "weight": 30}
                  ]
                }
                """;

        CustomEtfCreateRequestDTO request = objectMapper.readValue(json, CustomEtfCreateRequestDTO.class);

        assertEquals("나만의 주식 ETF", request.getTitle());
        assertEquals(3, request.getItems().size());
        assertEquals("KRX_373220", request.getItems().get(0).getStockId());
    }

    @Test
    void updateRequest_acceptsFrontendLegacyNameAndStocksFields() throws Exception {
        String json = """
                {
                  "name": "수정된 ETF",
                  "stocks": [
                    {"stockId": "US_AAPL", "weight": 100}
                  ]
                }
                """;

        CustomEtfUpdateRequestDTO request = objectMapper.readValue(json, CustomEtfUpdateRequestDTO.class);

        assertEquals("수정된 ETF", request.getTitle());
        assertEquals(1, request.getItems().size());
        assertEquals("US_AAPL", request.getItems().get(0).getStockId());
    }
}
