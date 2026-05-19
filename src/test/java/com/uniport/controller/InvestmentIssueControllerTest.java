package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.InvestmentIssueCategoryDTO;
import com.uniport.dto.InvestmentIssueDetailResponseDTO;
import com.uniport.dto.InvestmentIssueItemDTO;
import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.dto.InvestmentIssueRelatedEtfDTO;
import com.uniport.dto.InvestmentIssueRelatedStockDTO;
import com.uniport.dto.InvestmentIssueSharePreviewDTO;
import com.uniport.dto.InvestmentIssueShareRequestDTO;
import com.uniport.dto.InvestmentIssueShareResponseDTO;
import com.uniport.dto.InvestmentIssueSourceArticleDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.exception.GlobalExceptionHandler;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.InvestmentIssueService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvestmentIssueControllerTest {

    @Test
    void getIssues_delegatesQueryParametersAndSerializesListContract() throws Exception {
        InvestmentIssueService investmentIssueService = mock(InvestmentIssueService.class);
        when(investmentIssueService.getIssueList("THEME", "issue_cursor_1", 10)).thenReturn(
                InvestmentIssueListResponseDTO.builder()
                        .categories(List.of(
                                InvestmentIssueCategoryDTO.builder().category("ALL").label("전체").build(),
                                InvestmentIssueCategoryDTO.builder().category("MARKET").label("시황").build(),
                                InvestmentIssueCategoryDTO.builder().category("THEME").label("테마").build(),
                                InvestmentIssueCategoryDTO.builder().category("COMPANY").label("종목").build(),
                                InvestmentIssueCategoryDTO.builder().category("OVERSEAS").label("해외").build()
                        ))
                        .selectedCategory("THEME")
                        .heroIssue(issueItem("issue_20260519_hbm_semiconductor_8f3a12"))
                        .items(List.of(issueItem("issue_20260519_ai_infra_1a2b3c")))
                        .nextCursor("issue_20260519_ai_infra_1a2b3c")
                        .hasNext(true)
                        .build()
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new InvestmentIssueController(investmentIssueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/mock-investing/investment-issues")
                        .param("category", "THEME")
                        .param("cursor", "issue_cursor_1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].category").value("ALL"))
                .andExpect(jsonPath("$.categories[0].label").value("전체"))
                .andExpect(jsonPath("$.categories[2].category").value("THEME"))
                .andExpect(jsonPath("$.selectedCategory").value("THEME"))
                .andExpect(jsonPath("$.heroIssue.issueId").value("issue_20260519_hbm_semiconductor_8f3a12"))
                .andExpect(jsonPath("$.heroIssue.category").value("THEME"))
                .andExpect(jsonPath("$.heroIssue.categoryLabel").value("테마"))
                .andExpect(jsonPath("$.heroIssue.label").value("positive"))
                .andExpect(jsonPath("$.heroIssue.labelText").value("호재"))
                .andExpect(jsonPath("$.heroIssue.reasonBullets[0]").value("HBM 수요 확대 기대"))
                .andExpect(jsonPath("$.heroIssue.watchPoints[0]").value("단기 급등 종목은 변동성이 커질 수 있어요."))
                .andExpect(jsonPath("$.heroIssue.relatedStocks[0].symbol").value("005930"))
                .andExpect(jsonPath("$.heroIssue.relatedStocks[0].reason").value("HBM 공급 확대 기대와 직접 관련"))
                .andExpect(jsonPath("$.heroIssue.relatedEtfs[0].symbol").value("091160"))
                .andExpect(jsonPath("$.heroIssue.sourceCount").value(6))
                .andExpect(jsonPath("$.items[0].issueId").value("issue_20260519_ai_infra_1a2b3c"))
                .andExpect(jsonPath("$.nextCursor").value("issue_20260519_ai_infra_1a2b3c"))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(investmentIssueService).getIssueList("THEME", "issue_cursor_1", 10);
    }

    @Test
    void getIssue_delegatesIssueIdAndSerializesSourceArticles() throws Exception {
        InvestmentIssueService investmentIssueService = mock(InvestmentIssueService.class);
        when(investmentIssueService.getIssueDetail("issue_20260519_hbm_semiconductor_8f3a12")).thenReturn(
                InvestmentIssueDetailResponseDTO.builder()
                        .issueId("issue_20260519_hbm_semiconductor_8f3a12")
                        .title("HBM 기대감에 반도체주 강세")
                        .category("THEME")
                        .categoryLabel("테마")
                        .label("positive")
                        .labelText("호재")
                        .summary("AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리고 있어요.")
                        .body("HBM 수요 기대가 반도체 업종 전반의 투자 심리에 영향을 주고 있어요.")
                        .reasonBullets(List.of("HBM 수요 확대 기대", "AI 인프라 투자 증가와 연결"))
                        .watchPoints(List.of("단기 급등 종목은 변동성이 커질 수 있어요."))
                        .relatedStocks(List.of(relatedStock()))
                        .relatedEtfs(List.of(relatedEtf()))
                        .sourceCount(6)
                        .publishedAt("2026-05-19T09:30:00+09:00")
                        .updatedAt("2026-05-19T09:45:00+09:00")
                        .sourceArticles(List.of(InvestmentIssueSourceArticleDTO.builder()
                                .articleId("ARTICLE_20260519_1001")
                                .sourceName("한국경제")
                                .title("HBM 수요 기대 확대")
                                .summary("AI 서버 투자 확대가 HBM 수요 기대와 연결되고 있다는 기사입니다.")
                                .publishedAt("2026-05-19T09:30:00+09:00")
                                .externalUrl("https://www.hankyung.com/example")
                                .build()))
                        .build()
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new InvestmentIssueController(investmentIssueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/mock-investing/investment-issues/issue_20260519_hbm_semiconductor_8f3a12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueId").value("issue_20260519_hbm_semiconductor_8f3a12"))
                .andExpect(jsonPath("$.body").value("HBM 수요 기대가 반도체 업종 전반의 투자 심리에 영향을 주고 있어요."))
                .andExpect(jsonPath("$.sourceArticles[0].articleId").value("ARTICLE_20260519_1001"))
                .andExpect(jsonPath("$.sourceArticles[0].sourceName").value("한국경제"))
                .andExpect(jsonPath("$.sourceArticles[0].externalUrl").value("https://www.hankyung.com/example"));

        verify(investmentIssueService).getIssueDetail("issue_20260519_hbm_semiconductor_8f3a12");
    }

    @Test
    void getIssues_returnsBadRequestWhenServiceRejectsUnsupportedCategory() throws Exception {
        InvestmentIssueService investmentIssueService = mock(InvestmentIssueService.class);
        when(investmentIssueService.getIssueList("CRYPTO", null, null))
                .thenThrow(new ApiException("unsupported investment issue category: CRYPTO", HttpStatus.BAD_REQUEST));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new InvestmentIssueController(investmentIssueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/mock-investing/investment-issues")
                        .param("category", "CRYPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("unsupported investment issue category: CRYPTO"))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verify(investmentIssueService).getIssueList("CRYPTO", null, null);
    }

    @Test
    void shareInvestmentIssueToChatRoom_resolvesCurrentUserAndDelegatesToService() throws Exception {
        InvestmentIssueService investmentIssueService = mock(InvestmentIssueService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder().nickname("이슈공유러").build();
        currentUser.setId(7L);
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        when(investmentIssueService.shareInvestmentIssue(
                eq(3L),
                eq(currentUser),
                any(InvestmentIssueShareRequestDTO.class)
        )).thenReturn(InvestmentIssueShareResponseDTO.builder()
                .messageId(99L)
                .chatRoomId(3L)
                .type("INVESTMENT_ISSUE_SHARE")
                .issue(InvestmentIssueSharePreviewDTO.builder()
                        .issueId("issue_20260519_hbm_semiconductor_8f3a12")
                        .title("HBM 기대감에 반도체주 강세")
                        .label("positive")
                        .labelText("호재")
                        .summary("AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리고 있어요.")
                        .relatedStocks(List.of("삼성전자", "SK하이닉스"))
                        .sourceCount(6)
                        .build())
                .createdAt("2026-05-19T03:00:00Z")
                .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new InvestmentIssueShareController(investmentIssueService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/chat/rooms/3/messages/investment-issue")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueId\":\"issue_20260519_hbm_semiconductor_8f3a12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(99))
                .andExpect(jsonPath("$.chatRoomId").value(3))
                .andExpect(jsonPath("$.type").value("INVESTMENT_ISSUE_SHARE"))
                .andExpect(jsonPath("$.issue.issueId").value("issue_20260519_hbm_semiconductor_8f3a12"))
                .andExpect(jsonPath("$.issue.title").value("HBM 기대감에 반도체주 강세"))
                .andExpect(jsonPath("$.issue.labelText").value("호재"))
                .andExpect(jsonPath("$.issue.relatedStocks[0]").value("삼성전자"));

        verify(investmentIssueService).shareInvestmentIssue(
                eq(3L),
                eq(currentUser),
                any(InvestmentIssueShareRequestDTO.class)
        );
    }

    private InvestmentIssueItemDTO issueItem(String issueId) {
        return InvestmentIssueItemDTO.builder()
                .issueId(issueId)
                .title("HBM 기대감에 반도체주 강세")
                .category("THEME")
                .categoryLabel("테마")
                .label("positive")
                .labelText("호재")
                .summary("AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리며 반도체 관련 종목들이 주목받고 있어요.")
                .reasonBullets(List.of("HBM 수요 확대 기대", "AI 인프라 투자 증가와 연결"))
                .watchPoints(List.of("단기 급등 종목은 변동성이 커질 수 있어요."))
                .relatedStocks(List.of(relatedStock()))
                .relatedEtfs(List.of(relatedEtf()))
                .sourceCount(6)
                .publishedAt("2026-05-19T09:30:00+09:00")
                .updatedAt("2026-05-19T09:45:00+09:00")
                .build();
    }

    private InvestmentIssueRelatedStockDTO relatedStock() {
        return InvestmentIssueRelatedStockDTO.builder()
                .name("삼성전자")
                .symbol("005930")
                .market("KOSPI")
                .reason("HBM 공급 확대 기대와 직접 관련")
                .build();
    }

    private InvestmentIssueRelatedEtfDTO relatedEtf() {
        return InvestmentIssueRelatedEtfDTO.builder()
                .name("KODEX 반도체")
                .symbol("091160")
                .reason("반도체 업종 전반에 투자하는 ETF")
                .build();
    }
}
