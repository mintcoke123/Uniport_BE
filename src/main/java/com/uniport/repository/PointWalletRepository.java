package com.uniport.repository;

import com.uniport.entity.PointWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
    Optional<PointWallet> findByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM PointWallet w WHERE w.user.id = :userId")
    Optional<PointWallet> findByUser_IdForUpdate(@Param("userId") Long userId);

    List<PointWallet> findAllByOrderByUpdatedAtDesc();
    void deleteByUser_Id(Long userId);
}
