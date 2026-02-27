package com.uniport.service.importer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MST 한 행 파싱 결과 (code 정규화 후).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedRow {

    private String code;    // 6자리
    private String stdCode;
    private String nameKr;
}
