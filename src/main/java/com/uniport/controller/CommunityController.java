package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.CommunityCommentCreateRequestDTO;
import com.uniport.dto.CommunityCommentMutationResponseDTO;
import com.uniport.dto.CommunityCommentsResponseDTO;
import com.uniport.dto.CommunityLikeResponseDTO;
import com.uniport.dto.CommunityPostCreateRequestDTO;
import com.uniport.dto.CommunityPostDetailDTO;
import com.uniport.dto.CommunityPostMutationResponseDTO;
import com.uniport.dto.CommunityPostUpdateRequestDTO;
import com.uniport.dto.CommunityPostsResponseDTO;
import com.uniport.dto.CommunityReportRequestDTO;
import com.uniport.dto.CommunityReportResponseDTO;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.InvestorSentimentDTO;
import com.uniport.entity.User;
import com.uniport.service.CommunityService;
import com.uniport.service.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
@Tag(name = "Community", description = "커뮤니티 피드, 게시글, 댓글, 신고 API")
public class CommunityController {

    private final CommunityService communityService;
    private final CurrentUserResolver currentUserResolver;

    public CommunityController(CommunityService communityService,
                               CurrentUserResolver currentUserResolver) {
        this.communityService = communityService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/posts")
    @Operation(summary = "커뮤니티 피드 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CommunityPostsResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 정렬/타입/커서",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityPostsResponseDTO> getPosts(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(example = "LATEST")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(example = "GENERAL")
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "stockCode", required = false) String stockCode,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @Parameter(example = "POST_101")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        User viewer = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(communityService.getPosts(viewer, sort, type, stockCode, sentiment, cursor, size));
    }

    @GetMapping("/stocks/{stockCode}/sentiment")
    public ResponseEntity<InvestorSentimentDTO> getStockSentiment(@PathVariable String stockCode) {
        return ResponseEntity.ok(communityService.getInvestorSentiment(stockCode));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "게시글 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CommunityPostDetailDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityPostDetailDTO> getPost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId) {
        User viewer = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(communityService.getPost(viewer, postId));
    }

    @PostMapping("/posts")
    @Operation(summary = "게시글 작성", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "작성 성공",
                    content = @Content(schema = @Schema(implementation = CommunityPostMutationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "422", description = "타입별 필수값 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityPostMutationResponseDTO> createPost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CommunityPostCreateRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.createPost(user, request));
    }

    @PatchMapping("/posts/{postId}")
    @Operation(summary = "게시글 수정", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = CommunityPostMutationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityPostMutationResponseDTO> updatePost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId,
            @RequestBody CommunityPostUpdateRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(communityService.updatePost(user, postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "게시글 삭제", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        communityService.deletePost(user, postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/likes")
    @Operation(summary = "게시글 좋아요", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 성공",
                    content = @Content(schema = @Schema(implementation = CommunityLikeResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "이미 좋아요한 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityLikeResponseDTO> likePost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(communityService.likePost(user, postId));
    }

    @DeleteMapping("/posts/{postId}/likes")
    @Operation(summary = "게시글 좋아요 취소", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 취소 성공",
                    content = @Content(schema = @Schema(implementation = CommunityLikeResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "좋아요하지 않은 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityLikeResponseDTO> unlikePost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(communityService.unlikePost(user, postId));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "댓글 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CommunityCommentsResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 커서",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityCommentsResponseDTO> getComments(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        User viewer = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(communityService.getComments(viewer, postId, cursor, size));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "댓글 작성", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "작성 성공",
                    content = @Content(schema = @Schema(implementation = CommunityCommentMutationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityCommentMutationResponseDTO> createComment(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId,
            @RequestBody CommunityCommentCreateRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.createComment(user, postId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "댓글 삭제", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "댓글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String commentId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        communityService.deleteComment(user, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/reports")
    @Operation(summary = "게시글 신고", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공",
                    content = @Content(schema = @Schema(implementation = CommunityReportResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "게시글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "이미 신고한 게시글",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityReportResponseDTO> reportPost(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String postId,
            @RequestBody CommunityReportRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.reportPost(user, postId, request));
    }

    @PostMapping("/comments/{commentId}/reports")
    @Operation(summary = "댓글 신고", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공",
                    content = @Content(schema = @Schema(implementation = CommunityReportResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "댓글 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "이미 신고한 댓글",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CommunityReportResponseDTO> reportComment(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String commentId,
            @RequestBody CommunityReportRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.reportComment(user, commentId, request));
    }
}
