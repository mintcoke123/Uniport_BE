package com.uniport.repository;

import com.uniport.entity.ManagedCommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedCommunityPostLikeRepository extends JpaRepository<ManagedCommunityPostLike, Long> {
    boolean existsByPost_IdAndUserId(Long postId, Long userId);

    long countByPost_Id(Long postId);

    void deleteByPost_IdAndUserId(Long postId, Long userId);
}
