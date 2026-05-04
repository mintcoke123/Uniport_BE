package com.uniport.repository;

import com.uniport.entity.ManagedEtf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManagedEtfRepository extends JpaRepository<ManagedEtf, Long> {
    Optional<ManagedEtf> findByEtfCode(String etfCode);
    List<ManagedEtf> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);
    List<ManagedEtf> findBySourceTypeOrderByPublishedAtDesc(String sourceType);
    List<ManagedEtf> findBySourceTypeIsNullOrSourceTypeOrderByPublishedAtDesc(String sourceType);
}
