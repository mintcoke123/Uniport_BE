package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.EducationCatalogResponseDTO;
import com.uniport.dto.EducationDayCompleteResponseDTO;
import com.uniport.dto.EducationDayContentResponseDTO;
import com.uniport.dto.EducationQuizResponseDTO;
import com.uniport.dto.EducationQuizSubmitRequestDTO;
import com.uniport.dto.EducationQuizSubmitResponseDTO;
import com.uniport.dto.LearningCourseDetailResponseDTO;
import com.uniport.dto.LearningCourseStartResponseDTO;
import com.uniport.dto.LearningCoursesResponseDTO;
import com.uniport.dto.LearningDayCompleteResponseDTO;
import com.uniport.dto.LearningDayContentResponseDTO;
import com.uniport.dto.LearningHomeResponseDTO;
import com.uniport.dto.LearningStepSubmitRequestDTO;
import com.uniport.dto.LearningStepSubmitResponseDTO;
import com.uniport.service.LearningService;
import com.uniport.service.EducationContentService;
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
@RequestMapping("/api/learning")
@Tag(name = "Learning", description = "학습 홈, 코스, Day, Step API")
@SecurityRequirement(name = "firebaseBearerAuth")
public class LearningController {

    private final LearningService learningService;
    private final EducationContentService educationContentService;

    public LearningController(LearningService learningService, EducationContentService educationContentService) {
        this.learningService = learningService;
        this.educationContentService = educationContentService;
    }

    @GetMapping("/education/catalog")
    @Operation(summary = "교육 콘텐츠 카탈로그 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카탈로그 조회 성공",
                    content = @Content(schema = @Schema(implementation = EducationCatalogResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EducationCatalogResponseDTO> getEducationCatalog(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(educationContentService.getCatalog());
    }

    @GetMapping("/education/days/{track}/{day}")
    @Operation(summary = "교육 Day 콘텐츠 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Day 콘텐츠 조회 성공",
                    content = @Content(schema = @Schema(implementation = EducationDayContentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "콘텐츠 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EducationDayContentResponseDTO> getEducationDayContent(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String track,
            @PathVariable Integer day,
            @RequestParam(value = "sector", required = false) String sector) {
        return ResponseEntity.ok(educationContentService.getDayContent(track, day, sector));
    }

    @GetMapping("/education/quizzes/{track}/{day}")
    @Operation(summary = "교육 퀴즈 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "퀴즈 조회 성공",
                    content = @Content(schema = @Schema(implementation = EducationQuizResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "퀴즈 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EducationQuizResponseDTO> getEducationQuiz(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String track,
            @PathVariable Integer day,
            @RequestParam(value = "sector", required = false) String sector,
            @RequestParam(value = "mode", required = false) String mode) {
        return ResponseEntity.ok(educationContentService.getQuiz(track, day, sector, mode));
    }

    @PostMapping("/education/quizzes/{track}/{day}/submit")
    @Operation(summary = "교육 퀴즈 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "퀴즈 제출 성공",
                    content = @Content(schema = @Schema(implementation = EducationQuizSubmitResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "퀴즈 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EducationQuizSubmitResponseDTO> submitEducationQuiz(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String track,
            @PathVariable Integer day,
            @RequestParam(value = "sector", required = false) String sector,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestBody EducationQuizSubmitRequestDTO request) {
        return ResponseEntity.ok(educationContentService.submitQuiz(authenticatedUser.getUser(), track, day, sector, mode, request));
    }

    @PostMapping("/education/days/{track}/{day}/complete")
    @Operation(summary = "교육 Day 완료")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Day 완료 성공",
                    content = @Content(schema = @Schema(implementation = EducationDayCompleteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "완료 조건 미충족 또는 중복 완료",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Day 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EducationDayCompleteResponseDTO> completeEducationDay(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String track,
            @PathVariable Integer day,
            @RequestParam(value = "sector", required = false) String sector) {
        return ResponseEntity.ok(educationContentService.completeDay(authenticatedUser.getUser(), track, day, sector));
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
