package com.uniport.repository;

import com.uniport.entity.ManagedCommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManagedCommunityCommentRepository extends JpaRepository<ManagedCommunityComment, Long> {
    List<ManagedCommunityComment> findByPost_IdOrderByCreatedAtAsc(Long postId);

    long countByPost_Id(Long postId);
}
