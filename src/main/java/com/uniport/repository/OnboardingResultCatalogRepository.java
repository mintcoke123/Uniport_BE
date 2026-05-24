package com.uniport.repository;

import com.uniport.entity.OnboardingResultCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingResultCatalogRepository extends JpaRepository<OnboardingResultCatalog, Integer> {

    Optional<OnboardingResultCatalog> findByCharacterIdAndActiveTrue(Integer characterId);

    List<OnboardingResultCatalog> findAllByActiveTrueOrderByCharacterIdAsc();
}
