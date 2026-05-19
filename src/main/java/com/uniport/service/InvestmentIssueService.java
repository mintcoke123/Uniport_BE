package com.uniport.service;

import com.uniport.dto.InvestmentIssueCategoryDTO;
import com.uniport.dto.InvestmentIssueDetailResponseDTO;
import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class InvestmentIssueService {

    private enum InvestmentIssueCategory {
        ALL("전체"),
        MARKET("시황"),
        THEME("테마"),
        COMPANY("종목"),
        OVERSEAS("해외");

        private final String label;

        InvestmentIssueCategory(String label) {
            this.label = label;
        }
    }

    @Transactional(readOnly = true)
    public InvestmentIssueListResponseDTO getIssueList(String category, String cursor, Integer size) {
        InvestmentIssueCategory selectedCategory = parseCategory(category);
        // cursor and size are reserved for the later feed implementation; this task defines the contract only.
        return InvestmentIssueListResponseDTO.builder()
                .categories(categories())
                .selectedCategory(selectedCategory.name())
                .heroIssue(null)
                .items(List.of())
                .nextCursor(null)
                .hasNext(false)
                .build();
    }

    @Transactional(readOnly = true)
    public InvestmentIssueDetailResponseDTO getIssueDetail(String issueId) {
        throw new ApiException("investment issue not found", HttpStatus.NOT_FOUND);
    }

    private InvestmentIssueCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return InvestmentIssueCategory.ALL;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(InvestmentIssueCategory.values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ApiException("unsupported investment issue category: " + category,
                        HttpStatus.BAD_REQUEST));
    }

    private List<InvestmentIssueCategoryDTO> categories() {
        return Arrays.stream(InvestmentIssueCategory.values())
                .map(category -> InvestmentIssueCategoryDTO.builder()
                        .category(category.name())
                        .label(category.label)
                        .build())
                .toList();
    }
}
