package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.exception.ApiException;
import com.uniport.repository.CompetitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 대회 CRUD 및 진행 중 대회 조회. 어드민에서 설정한 종료일을 홈/대회 API에서 사용.
 */
@Service
public class CompetitionService {

    private final CompetitionRepository competitionRepository;

    public CompetitionService(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    public List<Competition> findAll() {
        return competitionRepository.findAll();
    }

    /** 진행 중인 대회 하나 (status=ongoing). 없으면 empty. */
    public java.util.Optional<Competition> findOngoing() {
        return competitionRepository.findFirstByStatusOrderByStartDateAsc("ongoing");
    }

    public List<Competition> findByStatus(String status) {
        return competitionRepository.findByStatusOrderByStartDateAsc(status);
    }

    @Transactional
    public Competition create(String name, String startDate, String endDate) {
        if (endDate != null && startDate != null && endDate.compareTo(startDate) <= 0) {
            throw new ApiException("종료일은 시작일보다 이후여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        Competition c = Competition.builder()
                .name(name != null ? name : "새 대회")
                .startDate(startDate != null ? startDate : "2025-03-01T00:00:00")
                .endDate(endDate != null ? endDate : "2025-03-31T23:59:59")
                .status("upcoming")
                .build();
        return competitionRepository.save(c);
    }

    @Transactional
    public Competition update(Long id, String name, String startDate, String endDate, String status) {
        Competition c = competitionRepository.findById(id)
                .orElseThrow(() -> new ApiException("대회를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (endDate != null && startDate != null && endDate.compareTo(startDate) <= 0) {
            throw new ApiException("종료일은 시작일보다 이후여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (name != null) c.setName(name);
        if (startDate != null) c.setStartDate(startDate);
        if (endDate != null) c.setEndDate(endDate);
        if (status != null && ("ongoing".equals(status) || "upcoming".equals(status) || "ended".equals(status))) {
            c.setStatus(status);
        }
        return competitionRepository.save(c);
    }

    public Map<String, Object> toMap(Competition c) {
        return Map.of(
                "id", c.getId(),
                "name", c.getName(),
                "startDate", c.getStartDate(),
                "endDate", c.getEndDate(),
                "status", c.getStatus() != null ? c.getStatus() : "upcoming"
        );
    }

    /** endDate 문자열에서 날짜만 추출해 남은 일수 계산 (날짜만 비교). */
    public int daysRemaining(String endDate) {
        if (endDate == null || endDate.isBlank()) return 0;
        try {
            String datePart = endDate.length() >= 10 ? endDate.substring(0, 10) : endDate;
            LocalDate end = LocalDate.parse(datePart);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end);
        } catch (Exception e) {
            return 0;
        }
    }
}
