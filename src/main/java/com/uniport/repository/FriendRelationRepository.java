package com.uniport.repository;

import com.uniport.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {
    List<FriendRelation> findAllByOrderByUpdatedAtDesc();
    void deleteByRequesterUser_IdOrAddresseeUser_Id(Long requesterUserId, Long addresseeUserId);

    @Query("""
            select fr from FriendRelation fr
            join fetch fr.requesterUser
            join fetch fr.addresseeUser
            where fr.requesterUser.id = :requesterUserId
               or fr.addresseeUser.id = :addresseeUserId
            order by fr.updatedAt desc
            """)
    List<FriendRelation> findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(
            @Param("requesterUserId") Long requesterUserId,
            @Param("addresseeUserId") Long addresseeUserId
    );

    @Query("""
            select fr from FriendRelation fr
            join fetch fr.requesterUser
            where fr.addresseeUser.id = :addresseeUserId
              and fr.status = :status
            order by fr.createdAt desc
            """)
    List<FriendRelation> findByAddresseeUser_IdAndStatusOrderByCreatedAtDesc(
            @Param("addresseeUserId") Long addresseeUserId,
            @Param("status") String status
    );

    @Query("""
            select fr from FriendRelation fr
            join fetch fr.addresseeUser
            where fr.requesterUser.id = :requesterUserId
              and fr.status = :status
            order by fr.createdAt desc
            """)
    List<FriendRelation> findByRequesterUser_IdAndStatusOrderByCreatedAtDesc(
            @Param("requesterUserId") Long requesterUserId,
            @Param("status") String status
    );

    @Query("""
            select fr from FriendRelation fr
            where (fr.requesterUser.id = :userId and fr.addresseeUser.id = :otherUserId)
               or (fr.requesterUser.id = :otherUserId and fr.addresseeUser.id = :userId)
            """)
    Optional<FriendRelation> findBetweenUsers(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId
    );

    @Query("""
            select fr from FriendRelation fr
            where ((fr.requesterUser.id = :userId and fr.addresseeUser.id = :otherUserId)
                or (fr.requesterUser.id = :otherUserId and fr.addresseeUser.id = :userId))
              and fr.status = :status
            """)
    Optional<FriendRelation> findBetweenUsersByStatus(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId,
            @Param("status") String status
    );
}
