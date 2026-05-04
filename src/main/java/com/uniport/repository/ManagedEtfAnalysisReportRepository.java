package com.uniport.repository;

import com.uniport.entity.ManagedEtfAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedEtfAnalysisReportRepository extends JpaRepository<ManagedEtfAnalysisReport, Long> {
    Optional<ManagedEtfAnalysisReport> findByReportId(String reportId);
}
