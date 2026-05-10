package com.uniport.repository;

import com.uniport.entity.GroupInvestmentFeedbackReport;
import com.uniport.entity.GroupInvestmentMemberFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupInvestmentMemberFeedbackRepository extends JpaRepository<GroupInvestmentMemberFeedback, Long> {

    List<GroupInvestmentMemberFeedback> findByReportOrderBySortOrderAsc(GroupInvestmentFeedbackReport report);

    void deleteByReport(GroupInvestmentFeedbackReport report);
}
