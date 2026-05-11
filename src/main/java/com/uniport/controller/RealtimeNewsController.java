package com.uniport.controller;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.RealtimeNewsDetailResponseDTO;
import com.uniport.dto.RealtimeNewsListResponseDTO;
import com.uniport.service.NewsService;
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
@RequestMapping("/api/mock-investing/realtime-news")
@Tag(name = "Realtime News", description = "모의투자 실시간 뉴스 API")
public class RealtimeNewsController {

    private final NewsService newsService;

    public RealtimeNewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    @Operation(summary = "실시간 뉴스 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = RealtimeNewsListResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 카테고리",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<RealtimeNewsListResponseDTO> getRealtimeNewsList(
            @Parameter(example = "ALL")
            @RequestParam(value = "category", required = false) String category,
            @Parameter(example = "NEWS_20260511_002")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(example = "20")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(newsService.getRealtimeNewsList(category, cursor, size));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "실시간 뉴스 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = RealtimeNewsDetailResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "뉴스 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<RealtimeNewsDetailResponseDTO> getRealtimeNewsDetail(@PathVariable String newsId) {
        return ResponseEntity.ok(newsService.getRealtimeNewsDetail(newsId));
    }
}
