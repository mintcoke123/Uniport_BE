package com.uniport.repository;

import com.uniport.entity.ManagedNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ManagedNewsArticleRepository extends JpaRepository<ManagedNewsArticle, Long> {
    Optional<ManagedNewsArticle> findByNewsKey(String newsKey);

    List<ManagedNewsArticle> findAllByOrderByPublishedAtDescIdDesc();

    @Query("""
            select a
            from ManagedNewsArticle a
            where (:stockCode is not null and upper(coalesce(a.stockCode, '')) = upper(:stockCode))
               or (:stockName is not null and upper(coalesce(a.stockName, '')) like concat('%', upper(:stockName), '%'))
            order by a.publishedAt desc, a.id desc
            """)
    List<ManagedNewsArticle> searchByStock(String stockCode, String stockName);
}
