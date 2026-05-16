package com.uniport.repository;

import com.uniport.entity.GifticonInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GifticonInventoryRepository extends JpaRepository<GifticonInventory, Long> {
    List<GifticonInventory> findByProduct_IdOrderByCreatedAtDesc(Long productId);

    @Query(
            value = """
                    SELECT *
                    FROM gifticon_inventory
                    WHERE product_id = :productId
                      AND status = :status
                    ORDER BY created_at ASC
                    LIMIT 1
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    GifticonInventory findFirstByProduct_IdAndStatusOrderByCreatedAtAsc(
            @Param("productId") Long productId,
            @Param("status") String status
    );

    long countByProduct_IdAndStatus(Long productId, String status);
}
