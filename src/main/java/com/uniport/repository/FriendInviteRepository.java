package com.uniport.repository;

import com.uniport.entity.FriendInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, Long> {
    Optional<FriendInvite> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM FriendInvite f
            WHERE f.inviterUser.id = :userId OR f.acceptedByUser.id = :userId
            """)
    void deleteAllByUserId(@Param("userId") Long userId);
}
