package com.uniport.repository;

import com.uniport.entity.PointShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointShopProductRepository extends JpaRepository<PointShopProduct, Long> {
    List<PointShopProduct> findAllByOrderBySortOrderAscCreatedAtDesc();
}
