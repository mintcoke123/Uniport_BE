package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 명세 §1 등: 실패 시 응답. success: false, message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDTO {

    private boolean success = false;
    private String message;
}
