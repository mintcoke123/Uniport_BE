package com.uniport.repository;

import com.uniport.entity.ManagedNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedNewsArticleRepository extends JpaRepository<ManagedNewsArticle, Long> {
    Optional<ManagedNewsArticle> findByNewsKey(String newsKey);
}
