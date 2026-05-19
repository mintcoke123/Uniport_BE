package com.uniport.controller;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.InvestmentIssueDetailResponseDTO;
import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.service.InvestmentIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock-investing/investment-issues")
@Tag(name = "Investment Issues", description = "모의투자 투자 이슈 API")
public class InvestmentIssueController {

    private final InvestmentIssueService investmentIssueService;

    public InvestmentIssueController(InvestmentIssueService investmentIssueService) {
        this.investmentIssueService = investmentIssueService;
    }

    @GetMapping
    @Operation(summary = "투자 이슈 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = InvestmentIssueListResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 카테고리",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<InvestmentIssueListResponseDTO> getIssues(
            @Parameter(example = "THEME")
            @RequestParam(value = "category", required = false) String category,
            @Parameter(example = "issue_20260519_ai_infra_1a2b3c")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(example = "20")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(investmentIssueService.getIssueList(category, cursor, size));
    }

    @GetMapping("/{issueId}")
    @Operation(summary = "투자 이슈 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = InvestmentIssueDetailResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "투자 이슈 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<InvestmentIssueDetailResponseDTO> getIssue(@PathVariable String issueId) {
        return ResponseEntity.ok(investmentIssueService.getIssueDetail(issueId));
    }
}
