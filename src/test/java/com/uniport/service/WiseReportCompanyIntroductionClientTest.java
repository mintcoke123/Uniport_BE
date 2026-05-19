package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
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
    void fetchFinancialData_parsesLatestAnnualWiseReportSummaryRows() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok("""
                                <html>
                                  <script>
                                    $.ajax({
                                      url: "ajax/cF1001.aspx",
                                      data: {
                                        cmp_cd: '005930',
                                        encparam: 'encodedToken',
                                        id: 'summaryNode' ? 'summaryNode' : ''
                                      }
                                    });
                                  </script>
                                </html>
                                """),
                        ResponseEntity.ok("""
                                <table class='gHead01 all-width' summary='주요재무정보를 제공합니다.'>
                                  <thead>
                                    <tr>
                                      <th rowspan="2">주요재무정보</th>
                                      <th colspan="4">연간</th>
                                      <th colspan="4">분기</th>
                                    </tr>
                                    <tr>
                                      <th>2022/12<br/><span>(IFRS연결)</span></th>
                                      <th>2023/12<br/><span>(IFRS연결)</span></th>
                                      <th>2024/12<br/><span>(IFRS연결)</span></th>
                                      <th>2025/12<br/><span>(IFRS연결)</span></th>
                                      <th>2025/03<br/><span>(IFRS연결)</span></th>
                                      <th>2025/06<br/><span>(IFRS연결)</span></th>
                                      <th>2025/09<br/><span>(IFRS연결)</span></th>
                                      <th>2025/12<br/><span>(IFRS연결)</span></th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    <tr>
                                      <th class="bg txt">매출액</th>
                                      <td title="3,022,313.60"><span>3,022,314</span></td>
                                      <td title="2,589,354.94"><span>2,589,355</span></td>
                                      <td title="3,008,709.03"><span>3,008,709</span></td>
                                      <td class="num " title="3,336,059.38"><span>&nbsp;</span></td>
                                      <td title="791,405.03"><span>791,405</span></td>
                                      <td title="745,663.17"><span>745,663</span></td>
                                      <td title="860,617.47"><span>860,617</span></td>
                                      <td title="938,373.71"><span>938,374</span></td>
                                    </tr>
                                    <tr>
                                      <th class="bg txt">영업이익</th>
                                      <td title="433,766.30"><span>433,766</span></td>
                                      <td title="65,669.76"><span>65,670</span></td>
                                      <td title="327,259.61"><span>327,260</span></td>
                                      <td class="num " title="436,010.51"><span>&nbsp;</span></td>
                                      <td title="66,852.72"><span>66,853</span></td>
                                      <td title="46,760.57"><span>46,761</span></td>
                                      <td title="121,660.62"><span>121,661</span></td>
                                      <td title="200,736.60"><span>200,737</span></td>
                                    </tr>
                                  </tbody>
                                </table>
                                """)
                );
        WiseReportCompanyIntroductionClient client =
                new WiseReportCompanyIntroductionClient(restTemplate, true, "https://navercomp.wisereport.co.kr");

        List<com.uniport.dto.FinancialDataItemDTO> result = client.fetchFinancialData("005930");

        assertEquals(1, result.size());
        assertEquals("2025/12", result.get(0).getQuarter());
        assertEquals(new BigDecimal("3336059"), result.get(0).getRevenue());
        assertEquals(new BigDecimal("436011"), result.get(0).getOperatingProfit());
        assertEquals("매출 3,336,059억원 · 영업이익 436,011억원", result.get(0).getValue());
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
