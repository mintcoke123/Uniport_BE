package com.uniport.repository;

import com.uniport.entity.ManagedCommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedCommunityPostRepository extends JpaRepository<ManagedCommunityPost, Long> {
}
