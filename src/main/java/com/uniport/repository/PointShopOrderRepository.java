package com.uniport.repository;

import com.uniport.entity.PointShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointShopOrderRepository extends JpaRepository<PointShopOrder, Long> {
    List<PointShopOrder> findAllByOrderByCreatedAtDesc();
    List<PointShopOrder> findByUser_IdOrderByCreatedAtDesc(Long userId);
    void deleteByUser_Id(Long userId);
}
