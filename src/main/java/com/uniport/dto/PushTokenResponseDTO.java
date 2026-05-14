package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTokenResponseDTO {
    private Long id;
    private String platform;
    private String permissionStatus;
    private boolean active;
    private String lastSeenAt;
}
