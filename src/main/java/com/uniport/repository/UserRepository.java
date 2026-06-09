package com.uniport.repository;

import com.uniport.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByStudentId(String studentId);

    Optional<User> findByEmail(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStudentId(String studentId);

    boolean existsByNickname(String nickname);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByTeamId(String teamId);

    List<User> findTop10ByNicknameContainingIgnoreCaseOrStudentIdContaining(String nickname, String studentId);
}
