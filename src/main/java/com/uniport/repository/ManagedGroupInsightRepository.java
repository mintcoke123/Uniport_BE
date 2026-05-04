package com.uniport.repository;

import com.uniport.entity.ManagedGroupInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedGroupInsightRepository extends JpaRepository<ManagedGroupInsight, Long> {
    Optional<ManagedGroupInsight> findByInsightKey(String insightKey);
}
