package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "투자 이슈 채팅방 공유 응답")
public class InvestmentIssueShareResponseDTO {

    @Schema(example = "99")
    private Long messageId;

    @Schema(example = "3")
    private Long chatRoomId;

    @Schema(example = "INVESTMENT_ISSUE_SHARE")
    private String type;

    private InvestmentIssueSharePreviewDTO issue;

    @Schema(example = "2026-05-19T03:00:00Z")
    private String createdAt;
}
