package com.uniport.repository;

import com.uniport.entity.UserAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {

    Optional<UserAuthIdentity> findByFirebaseUid(String firebaseUid);

    @Query("""
            SELECT i.firebaseUid
            FROM UserAuthIdentity i
            WHERE i.user.id = :userId
            """)
    List<String> findFirebaseUidsByUserId(@Param("userId") Long userId);

    void deleteByUser_Id(Long userId);

    @Query("""
            SELECT COUNT(identity) > 0
            FROM UserAuthIdentity identity
            WHERE identity.user.id = :userId
              AND identity.firebaseUid <> :firebaseUid
              AND (
                    (:providerId IS NULL AND identity.providerId IS NULL)
                    OR identity.providerId = :providerId
              )
            """)
    boolean existsOtherIdentityForUserAndProvider(@Param("userId") Long userId,
                                                  @Param("providerId") String providerId,
                                                  @Param("firebaseUid") String firebaseUid);

    @Modifying
    @Query(value = """
            INSERT INTO user_auth_identities (
                user_id,
                firebase_uid,
                provider_id,
                email,
                email_verified,
                created_at,
                updated_at
            )
            VALUES (
                :userId,
                :firebaseUid,
                :providerId,
                :email,
                :emailVerified,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (firebase_uid) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId,
                     @Param("firebaseUid") String firebaseUid,
                     @Param("providerId") String providerId,
                     @Param("email") String email,
                     @Param("emailVerified") boolean emailVerified);
}
