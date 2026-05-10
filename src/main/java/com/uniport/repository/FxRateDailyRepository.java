package com.uniport.repository;

import com.uniport.entity.FxRateDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FxRateDailyRepository extends JpaRepository<FxRateDaily, Long> {

    Optional<FxRateDaily> findByCurrencyAndRateDate(String currency, LocalDate rateDate);
}
