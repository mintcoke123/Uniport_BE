package com.uniport.repository;

import com.uniport.entity.BetaIosApplication;
import com.uniport.entity.BetaIosApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BetaIosApplicationRepository extends JpaRepository<BetaIosApplication, Long> {
    Optional<BetaIosApplication> findByAppleIdEmail(String appleIdEmail);

    List<BetaIosApplication> findTop50ByStatusInOrderByUpdatedAtAsc(List<BetaIosApplicationStatus> statuses);
}
