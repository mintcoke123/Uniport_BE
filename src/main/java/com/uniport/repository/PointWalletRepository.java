package com.uniport.repository;

import com.uniport.entity.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
    Optional<PointWallet> findByUser_Id(Long userId);
    List<PointWallet> findAllByOrderByUpdatedAtDesc();
    void deleteByUser_Id(Long userId);
}
