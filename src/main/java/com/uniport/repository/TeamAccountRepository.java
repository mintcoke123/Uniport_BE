package com.uniport.repository;

import com.uniport.entity.TeamAccount;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamAccountRepository extends JpaRepository<TeamAccount, Long> {

    Optional<TeamAccount> findByTeamId(Long teamId);
}
