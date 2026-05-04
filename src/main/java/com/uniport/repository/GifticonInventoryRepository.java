package com.uniport.repository;

import com.uniport.entity.GifticonInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GifticonInventoryRepository extends JpaRepository<GifticonInventory, Long> {
    List<GifticonInventory> findByProduct_IdOrderByCreatedAtDesc(Long productId);
}
