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
@Schema(description = "투자 이슈 채팅방 공유 요청")
public class InvestmentIssueShareRequestDTO {

    @Schema(example = "issue_20260519_hbm_semiconductor_8f3a12")
    private String issueId;
}
