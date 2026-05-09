package com.uniport.repository;

import com.uniport.entity.FriendInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, Long> {
    Optional<FriendInvite> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
}
