package com.uniport.repository;

import com.uniport.entity.MatchingRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchingRoomRepository extends JpaRepository<MatchingRoom, Long> {

    List<MatchingRoom> findAllByOrderByCreatedAtDesc();
}
