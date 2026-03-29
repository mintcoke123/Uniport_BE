package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.UserSearchItemDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.UserSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "친구 초대용 사용자 검색 API")
public class ApiUserController {

    private final UserSearchService userSearchService;
    private final CurrentUserResolver currentUserResolver;

    public ApiUserController(UserSearchService userSearchService,
                             CurrentUserResolver currentUserResolver) {
        this.userSearchService = userSearchService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/search")
    @Operation(summary = "사용자 검색", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공",
                    content = @Content(schema = @Schema(implementation = UserSearchItemDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<List<UserSearchItemDTO>> searchUsers(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "검색어", example = "김")
            @RequestParam("keyword") String keyword,
            @Parameter(description = "최대 개수", example = "10")
            @RequestParam(value = "limit", required = false) Integer limit) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(userSearchService.search(user, keyword, limit));
    }
}
