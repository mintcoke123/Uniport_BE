package com.uniport.repository;

import com.uniport.entity.ManagedEtfFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedEtfFavoriteRepository extends JpaRepository<ManagedEtfFavorite, Long> {
    boolean existsByUserIdAndEtfCode(Long userId, String etfCode);
    long countByEtfCode(String etfCode);
    void deleteByUserIdAndEtfCode(Long userId, String etfCode);
}
