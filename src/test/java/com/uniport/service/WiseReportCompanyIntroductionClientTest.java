package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WiseReportCompanyIntroductionClientTest {

    @Test
    void fetchCompanyIntroduction_parsesWiseReportCompanyOverview() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        <html>
                          <body>
                            <h5><span>기업개요</span></h5>
                            <div class="cmp_comment">
                              <ul class="dot_cmp">
                                <li class="dot_cmp" data-cd="005930">동사는 1969년 설립된 글로벌 전자 기업으로 DX, DS 두 부문과 SDC, Harman으로 구성되어 있음.</li>
                                <li class="dot_cmp" data-cd="005930">TV, 모니터, 스마트폰, DRAM, NAND Flash 등을 생산·판매하고 있음.</li>
                              </ul>
                            </div>
                          </body>
                        </html>
                        """));
        WiseReportCompanyIntroductionClient client =
                new WiseReportCompanyIntroductionClient(restTemplate, true, "https://navercomp.wisereport.co.kr");

        Optional<String> result = client.fetchCompanyIntroduction("KRX_005930");

        assertEquals(
                "동사는 1969년 설립된 글로벌 전자 기업으로 DX, DS 두 부문과 SDC, Harman으로 구성되어 있음.\n" +
                        "TV, 모니터, 스마트폰, DRAM, NAND Flash 등을 생산·판매하고 있음.",
                result.orElseThrow()
        );
    }

    @Test
    void parseCompanyIntroduction_returnsEmptyWhenOverviewIsMissing() {
        Optional<String> result = WiseReportCompanyIntroductionClient.parseCompanyIntroduction("""
                <html>
                  <body>
                    <ul class="dot_cmp"><li class="dot_cmp">다른 영역 문장</li></ul>
                  </body>
                </html>
                """);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseCompanyIntroduction_usesOnlyOverviewCommentBlock() {
        Optional<String> result = WiseReportCompanyIntroductionClient.parseCompanyIntroduction("""
                <html>
                  <head><title>온라인기업정보 - 기업개요</title></head>
                  <body>
                    <ul class="dot_cmp"><li class="dot_cmp">다른 영역 문장</li></ul>
                    <h5><span>기업개요</span></h5>
                    <div class="cmp_comment">
                      <ul class="dot_cmp">
                        <li class="dot_cmp">실제 기업개요 문장</li>
                      </ul>
                    </div>
                    <ul class="dot_cmp"><li class="dot_cmp">후속 영역 문장</li></ul>
                  </body>
                </html>
                """);

        assertEquals("실제 기업개요 문장", result.orElseThrow());
    }
}
