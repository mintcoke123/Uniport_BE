package com.uniport.repository;

import com.uniport.entity.Vote;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByRoomIdOrderByCreatedAtDesc(Long roomId);
}
