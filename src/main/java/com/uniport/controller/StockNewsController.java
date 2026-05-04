package com.uniport.controller;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.StockNewsDetailResponseDTO;
import com.uniport.dto.StockNewsListResponseDTO;
import com.uniport.service.ManagedStockNewsService;
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
@RequestMapping("/api/news/stocks")
@Tag(name = "Stock News", description = "주식 뉴스 목록 및 상세 API")
public class StockNewsController {

    private final ManagedStockNewsService managedStockNewsService;

    public StockNewsController(ManagedStockNewsService managedStockNewsService) {
        this.managedStockNewsService = managedStockNewsService;
    }

    @GetMapping
    @Operation(summary = "주식 뉴스 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = StockNewsListResponseDTO.class)))
    })
    public ResponseEntity<StockNewsListResponseDTO> getNewsList(
            @Parameter(example = "삼성전자")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(example = "LATEST")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(managedStockNewsService.getNewsList(keyword, sort, page, size));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "주식 뉴스 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = StockNewsDetailResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "뉴스 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<StockNewsDetailResponseDTO> getNewsDetail(@PathVariable String newsId) {
        return ResponseEntity.ok(managedStockNewsService.getNewsDetail(newsId));
    }
}
