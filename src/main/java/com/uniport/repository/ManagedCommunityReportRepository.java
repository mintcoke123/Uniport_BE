package com.uniport.repository;

import com.uniport.entity.ManagedCommunityReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedCommunityReportRepository extends JpaRepository<ManagedCommunityReport, Long> {
    boolean existsByTargetTypeAndPostIdAndReporterUserId(String targetType, Long postId, Long reporterUserId);

    boolean existsByTargetTypeAndCommentIdAndReporterUserId(String targetType, Long commentId, Long reporterUserId);
}
