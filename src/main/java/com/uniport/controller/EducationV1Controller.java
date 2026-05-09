package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.service.EducationV1Service;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/education")
@Tag(name = "Education V1", description = "KMP 교육 화면용 API")
@SecurityRequirement(name = "firebaseBearerAuth")
public class EducationV1Controller {

    private final EducationV1Service educationV1Service;

    public EducationV1Controller(EducationV1Service educationV1Service) {
        this.educationV1Service = educationV1Service;
    }

    @GetMapping("/home")
    @Operation(summary = "KMP 교육 홈 요약 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교육 홈 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> getHome(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(educationV1Service.getHome(authenticatedUser.getUser()));
    }

    @GetMapping("/courses")
    @Operation(summary = "KMP 교육 코스 목록 조회")
    public ResponseEntity<Map<String, Object>> getCourses(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @RequestParam(value = "tab", required = false) String tab) {
        return ResponseEntity.ok(educationV1Service.getCourses(authenticatedUser.getUser(), tab));
    }

    @GetMapping("/courses/{courseId}/roadmap")
    @Operation(summary = "KMP 교육 코스 로드맵 조회")
    public ResponseEntity<Map<String, Object>> getCourseRoadmap(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String courseId) {
        return ResponseEntity.ok(educationV1Service.getCourseRoadmap(authenticatedUser.getUser(), courseId));
    }

    @GetMapping("/courses/{courseId}/days/{day}")
    @Operation(summary = "KMP 교육 Day 상세 조회")
    public ResponseEntity<Map<String, Object>> getCourseDay(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String courseId,
            @PathVariable Integer day) {
        return ResponseEntity.ok(educationV1Service.getCourseDay(authenticatedUser.getUser(), courseId, day));
    }

    @GetMapping("/courses/{courseId}/sector-selection")
    @Operation(summary = "KMP 교육 선택 섹터 조회")
    public ResponseEntity<Map<String, Object>> getSectorSelection(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String courseId) {
        return ResponseEntity.ok(educationV1Service.getSectorSelection(authenticatedUser.getUser(), courseId));
    }

    @PutMapping("/courses/{courseId}/sector-selection")
    @Operation(summary = "KMP 교육 선택 섹터 저장")
    public ResponseEntity<Map<String, Object>> updateSectorSelection(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(educationV1Service.updateSectorSelection(authenticatedUser.getUser(), courseId, request));
    }

    @GetMapping("/courses/{courseId}/days/{day}/quiz")
    @Operation(summary = "KMP 교육 Day 퀴즈 조회")
    public ResponseEntity<Map<String, Object>> getCourseDayQuiz(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String courseId,
            @PathVariable Integer day) {
        return ResponseEntity.ok(educationV1Service.getCourseDayQuiz(authenticatedUser.getUser(), courseId, day));
    }

    @GetMapping("/quiz/{quizId}")
    @Operation(summary = "KMP 교육 단일 퀴즈 조회")
    public ResponseEntity<Map<String, Object>> getQuiz(@PathVariable String quizId) {
        return ResponseEntity.ok(educationV1Service.getQuiz(quizId));
    }

    @PostMapping("/quiz-attempts")
    @Operation(summary = "KMP 교육 퀴즈 제출")
    public ResponseEntity<Map<String, Object>> submitQuizAttempt(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(educationV1Service.submitQuizAttempt(authenticatedUser.getUser(), request));
    }

    @PostMapping("/progress/cards/complete")
    @Operation(summary = "KMP 교육 카드 완료")
    public ResponseEntity<Map<String, Object>> completeCard(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(educationV1Service.completeCard(authenticatedUser.getUser(), request));
    }

    @PostMapping("/courses/{courseId}/days/{day}/complete")
    @Operation(summary = "KMP 교육 Day 완료")
    public ResponseEntity<Map<String, Object>> completeCourseDay(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @PathVariable String courseId,
            @PathVariable Integer day,
            @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.ok(educationV1Service.completeCourseDay(
                authenticatedUser.getUser(),
                courseId,
                day,
                request == null ? Map.of() : request));
    }
}
