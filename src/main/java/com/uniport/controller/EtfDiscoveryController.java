package com.uniport.controller;

import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.service.EtfMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etf-discovery")
@Tag(name = "ETF Discovery", description = "인기 ETF 탐색 API")
public class EtfDiscoveryController {

    private final EtfMockService etfMockService;

    public EtfDiscoveryController(EtfMockService etfMockService) {
        this.etfMockService = etfMockService;
    }

    @GetMapping("/popular")
    @Operation(summary = "인기 ETF 탐색 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = EtfDiscoveryResponseDTO.class)))
    })
    public ResponseEntity<EtfDiscoveryResponseDTO> getPopularEtfs(
            @Parameter(example = "RETURN", description = "정렬 기준: RETURN, POPULAR")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(example = "기술", description = "테마 필터")
            @RequestParam(value = "theme", required = false) String theme,
            @Parameter(example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(etfMockService.getPopularEtfs(sort, theme, page, size));
    }
}
