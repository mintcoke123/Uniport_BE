package com.uniport.repository;

import com.uniport.entity.InvestmentTestReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestmentTestReservationRepository extends JpaRepository<InvestmentTestReservation, Long> {
    Optional<InvestmentTestReservation> findByContactTypeAndContactValue(String contactType, String contactValue);
}
