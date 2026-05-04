package com.uniport.repository;

import com.uniport.entity.ManagedEtf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedEtfRepository extends JpaRepository<ManagedEtf, Long> {
    Optional<ManagedEtf> findByEtfCode(String etfCode);
}
