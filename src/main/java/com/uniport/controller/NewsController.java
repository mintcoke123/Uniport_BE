package com.uniport.controller;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.NewsItemResponseDTO;
import com.uniport.dto.NewsListResponseDTO;
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
@RequestMapping("/api/news")
@Tag(name = "News", description = "뉴스 목록 및 상세 API")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    @Operation(summary = "뉴스 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = NewsListResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 카테고리",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<NewsListResponseDTO> getNewsList(
            @Parameter(example = "ALL")
            @RequestParam(value = "category", required = false) String category,
            @Parameter(example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(example = "20")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(newsService.getNewsList(category, page, size));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "뉴스 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = NewsItemResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "뉴스 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<NewsItemResponseDTO> getNewsDetail(@PathVariable String newsId) {
        return ResponseEntity.ok(newsService.getNewsDetail(newsId));
    }
}
