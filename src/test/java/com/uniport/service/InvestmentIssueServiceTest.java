package com.uniport.service;

import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvestmentIssueServiceTest {

    private final InvestmentIssueService investmentIssueService = new InvestmentIssueService();

    @Test
    void getIssueList_acceptsAllSupportedCategories() {
        for (String category : List.of("ALL", "MARKET", "THEME", "COMPANY", "OVERSEAS")) {
            InvestmentIssueListResponseDTO response = investmentIssueService.getIssueList(category, null, 20);

            assertEquals(category, response.getSelectedCategory());
        }
    }

    @Test
    void getIssueList_defaultsNullAndBlankCategoryToAll() {
        assertEquals("ALL", investmentIssueService.getIssueList(null, null, null).getSelectedCategory());
        assertEquals("ALL", investmentIssueService.getIssueList("   ", null, null).getSelectedCategory());
    }

    @Test
    void getIssueList_rejectsUnsupportedCategoryWithBadRequest() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> investmentIssueService.getIssueList("CRYPTO", null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("BAD_REQUEST", exception.getErrorCode());
        assertEquals("unsupported investment issue category: CRYPTO", exception.getMessage());
    }

    @Test
    void getIssueList_returnsEmptySkeletonResponseShape() {
        InvestmentIssueListResponseDTO response = investmentIssueService.getIssueList("THEME", "cursor_1", 10);

        assertEquals(List.of("ALL", "MARKET", "THEME", "COMPANY", "OVERSEAS"),
                response.getCategories().stream().map(category -> category.getCategory()).toList());
        assertEquals(List.of("전체", "시황", "테마", "종목", "해외"),
                response.getCategories().stream().map(category -> category.getLabel()).toList());
        assertEquals("THEME", response.getSelectedCategory());
        assertNull(response.getHeroIssue());
        assertEquals(List.of(), response.getItems());
        assertNull(response.getNextCursor());
        assertFalse(response.getHasNext());
    }

    @Test
    void getIssueDetail_throwsNotFoundUntilFeedLogicIsImplemented() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> investmentIssueService.getIssueDetail("issue_missing")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("NOT_FOUND", exception.getErrorCode());
        assertEquals("investment issue not found", exception.getMessage());
    }
}
