package com.uniport.repository;

import com.uniport.entity.ManagedCommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ManagedCommunityPostRepository extends JpaRepository<ManagedCommunityPost, Long> {
    List<ManagedCommunityPost> findAllByOrderByCreatedAtDescIdDesc();

    List<ManagedCommunityPost> findByStockCodeOrderByCreatedAtDescIdDesc(String stockCode);

    @Query("""
            select p
            from ManagedCommunityPost p
            where (:type is null or upper(p.type) = :type)
              and (:stockCode is null or upper(coalesce(p.stockCode, '')) = :stockCode)
              and (:sentiment is null or upper(coalesce(p.sentiment, '')) = :sentiment)
            order by p.createdAt desc, p.id desc
            """)
    List<ManagedCommunityPost> search(String type, String stockCode, String sentiment);
}
