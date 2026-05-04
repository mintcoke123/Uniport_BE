package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyPageHistoryItemDTO {
    private String title;
    private String subtitle;
    private String valueLabel;
    private String statusLabel;
    private String happenedAtLabel;
}
