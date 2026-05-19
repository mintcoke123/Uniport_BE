package com.uniport.repository;

import com.uniport.entity.FestivalTradingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalTradingSessionRepository extends JpaRepository<FestivalTradingSession, Long> {

    List<FestivalTradingSession> findByEndedAtIsNotNullOrderByEndTotalValueDescEndedAtAsc();

    List<FestivalTradingSession> findAllByOrderByStartedAtDesc();
}
