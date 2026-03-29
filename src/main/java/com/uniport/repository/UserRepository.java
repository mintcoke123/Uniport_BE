package com.uniport.repository;

import com.uniport.entity.User;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByStudentId(String studentId);

    Optional<User> findByEmail(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByUsername(String username);

    boolean existsByStudentId(String studentId);

    boolean existsByNickname(String nickname);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findTop10ByNicknameContainingIgnoreCaseOrStudentIdContaining(String nickname, String studentId);
}
