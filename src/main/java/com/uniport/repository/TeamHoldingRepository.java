package com.uniport.repository;

import com.uniport.entity.TeamHolding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamHoldingRepository extends JpaRepository<TeamHolding, Long> {

    List<TeamHolding> findByTeamId(Long teamId);

    Optional<TeamHolding> findByTeamIdAndStockCode(Long teamId, String stockCode);
}
