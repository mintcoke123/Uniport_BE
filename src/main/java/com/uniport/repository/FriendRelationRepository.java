package com.uniport.repository;

import com.uniport.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {
    List<FriendRelation> findAllByOrderByUpdatedAtDesc();
    void deleteByRequesterUser_IdOrAddresseeUser_Id(Long requesterUserId, Long addresseeUserId);
}
