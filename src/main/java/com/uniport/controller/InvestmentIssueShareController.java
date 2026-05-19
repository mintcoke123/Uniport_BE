package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.InvestmentIssueShareRequestDTO;
import com.uniport.dto.InvestmentIssueShareResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.InvestmentIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Investment Issue Share", description = "투자 이슈 채팅방 공유 API")
public class InvestmentIssueShareController {

    private final InvestmentIssueService investmentIssueService;
    private final CurrentUserResolver currentUserResolver;

    public InvestmentIssueShareController(InvestmentIssueService investmentIssueService,
                                          CurrentUserResolver currentUserResolver) {
        this.investmentIssueService = investmentIssueService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/api/chat/rooms/{chatRoomId}/messages/investment-issue")
    @Operation(summary = "투자 이슈를 채팅방에 공유")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공유 성공",
                    content = @Content(schema = @Schema(implementation = InvestmentIssueShareResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "채팅방 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "투자 이슈 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<InvestmentIssueShareResponseDTO> shareInvestmentIssueToChatRoom(
            @PathVariable Long chatRoomId,
            @RequestBody(required = false) InvestmentIssueShareRequestDTO request,
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(investmentIssueService.shareInvestmentIssue(chatRoomId, user, request));
    }
}
