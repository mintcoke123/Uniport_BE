package com.uniport.repository;

import com.uniport.entity.BetaIosApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BetaIosApplicationRepository extends JpaRepository<BetaIosApplication, Long> {
    Optional<BetaIosApplication> findByAppleIdEmail(String appleIdEmail);
}
