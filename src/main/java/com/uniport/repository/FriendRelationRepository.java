package com.uniport.repository;

import com.uniport.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {
    List<FriendRelation> findAllByOrderByUpdatedAtDesc();
    void deleteByRequesterUser_IdOrAddresseeUser_Id(Long requesterUserId, Long addresseeUserId);
    List<FriendRelation> findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(Long requesterUserId, Long addresseeUserId);
    List<FriendRelation> findByAddresseeUser_IdAndStatusOrderByCreatedAtDesc(Long addresseeUserId, String status);
    List<FriendRelation> findByRequesterUser_IdAndStatusOrderByCreatedAtDesc(Long requesterUserId, String status);
    @Query("""
            select fr from FriendRelation fr
            where (fr.requesterUser.id = :userId and fr.addresseeUser.id = :otherUserId)
               or (fr.requesterUser.id = :otherUserId and fr.addresseeUser.id = :userId)
            """)
    Optional<FriendRelation> findBetweenUsers(Long userId, Long otherUserId);
}
