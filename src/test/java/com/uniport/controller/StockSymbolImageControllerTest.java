package com.uniport.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockSymbolImageControllerTest {

    @Test
    void rendersFallbackSymbolSvgFromQueryParameters() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StockSymbolImageController()).build();

        mockMvc.perform(get("/api/stock-symbols/NASDAQ/AAPL.svg")
                        .param("text", "AAPL")
                        .param("bg", "EEF2FF")
                        .param("fg", "4F46E5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().string(containsString(">AAPL<")))
                .andExpect(content().string(containsString("#EEF2FF")))
                .andExpect(content().string(containsString("#4F46E5")));
    }
}
