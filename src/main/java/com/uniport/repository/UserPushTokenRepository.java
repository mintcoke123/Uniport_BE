package com.uniport.repository;

import com.uniport.entity.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, Long> {

    Optional<UserPushToken> findByToken(String token);

    List<UserPushToken> findByUser_IdAndActiveTrue(Long userId);

    void deleteByUser_Id(Long userId);
}
