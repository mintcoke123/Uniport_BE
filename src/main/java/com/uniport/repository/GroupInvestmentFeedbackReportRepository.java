package com.uniport.repository;

import com.uniport.entity.GroupInvestmentFeedbackReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupInvestmentFeedbackReportRepository extends JpaRepository<GroupInvestmentFeedbackReport, Long> {

    Optional<GroupInvestmentFeedbackReport> findBySessionId(Long sessionId);

    boolean existsBySessionId(Long sessionId);
}
