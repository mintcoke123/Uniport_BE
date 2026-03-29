package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.LearningCourseDetailResponseDTO;
import com.uniport.dto.LearningCourseStartResponseDTO;
import com.uniport.dto.LearningCoursesResponseDTO;
import com.uniport.dto.LearningDayCompleteResponseDTO;
import com.uniport.dto.LearningDayContentResponseDTO;
import com.uniport.dto.LearningHomeResponseDTO;
import com.uniport.dto.LearningStepSubmitRequestDTO;
import com.uniport.dto.LearningStepSubmitResponseDTO;
import com.uniport.service.LearningService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/learning", "/learning"})
@Tag(name = "Learning", description = "학습 홈, 코스, Day, Step API")
@SecurityRequirement(name = "firebaseBearerAuth")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/home")
    @Operation(summary = "학습 홈 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "학습 홈 조회 성공",
                    content = @Content(schema = @Schema(implementation = LearningHomeResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "진행 중인 코스 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningHomeResponseDTO> getLearningHome(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(learningService.getHome(authenticatedUser.getUser()));
    }

    @GetMapping("/courses")
    @Operation(summary = "교육 코스 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "코스 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = LearningCoursesResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningCoursesResponseDTO> getLearningCourses(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @Parameter(description = "조회할 코스 카테고리", example = "MAIN")
            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.ok(learningService.getCourses(authenticatedUser.getUser(), category));
    }

    @PostMapping("/courses/{courseId}/start")
    @Operation(summary = "코스 시작")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "코스 시작 성공",
                    content = @Content(schema = @Schema(implementation = LearningCourseStartResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "잠긴 코스이거나 시작할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningCourseStartResponseDTO> startLearningCourse(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(learningService.startCourse(authenticatedUser.getUser(), courseId));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "코스 진입 정보 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "코스 진입 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = LearningCourseDetailResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningCourseDetailResponseDTO> getLearningCourseDetail(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(learningService.getCourseDetail(authenticatedUser.getUser(), courseId));
    }

    @GetMapping("/courses/{courseId}/days/{dayId}")
    @Operation(summary = "일일 학습 콘텐츠 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Day 콘텐츠 조회 성공",
                    content = @Content(schema = @Schema(implementation = LearningDayContentResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스 또는 Day",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningDayContentResponseDTO> getLearningDayContent(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable Long courseId,
            @PathVariable Integer dayId) {
        return ResponseEntity.ok(learningService.getDayContent(authenticatedUser.getUser(), courseId, dayId));
    }

    @PostMapping("/steps/{stepId}/submit")
    @Operation(summary = "문제 Step 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스텝 제출 성공",
                    content = @Content(schema = @Schema(implementation = LearningStepSubmitResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스텝",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningStepSubmitResponseDTO> submitLearningStep(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable Long stepId,
            @RequestBody LearningStepSubmitRequestDTO request) {
        return ResponseEntity.ok(learningService.submitStep(authenticatedUser.getUser(), stepId, request));
    }

    @PostMapping("/courses/{courseId}/days/{dayId}/complete")
    @Operation(summary = "Day 완료")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Day 완료 처리 성공",
                    content = @Content(schema = @Schema(implementation = LearningDayCompleteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "이미 완료된 Day 또는 완료 조건 불충족",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스 또는 Day",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<LearningDayCompleteResponseDTO> completeLearningDay(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable Long courseId,
            @PathVariable Integer dayId) {
        return ResponseEntity.ok(learningService.completeDay(authenticatedUser.getUser(), courseId, dayId));
    }
}
