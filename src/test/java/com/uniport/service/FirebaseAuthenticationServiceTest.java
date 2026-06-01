package com.uniport.service;

import com.google.firebase.auth.FirebaseToken;
import com.uniport.config.FirebaseProperties;
import com.uniport.entity.UserAuthIdentity;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserAuthIdentityRepository;
import com.uniport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseAuthenticationServiceTest {

    private final FirebaseProperties firebaseProperties = new FirebaseProperties();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAuthIdentityRepository userAuthIdentityRepository = mock(UserAuthIdentityRepository.class);
    private final UserDeletionReferenceCleanupService userDeletionReferenceCleanupService = mock(UserDeletionReferenceCleanupService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final FirebaseAuthenticationService service = new FirebaseAuthenticationService(
            firebaseProperties,
            userRepository,
            userAuthIdentityRepository,
            userDeletionReferenceCleanupService,
            passwordEncoder
    );

    @Test
    void firebaseUidIdentityResolvesLinkedUser() {
        FirebaseToken token = firebaseToken(
                "incoming-apple-uid",
                "kwakkun2002@gmail.com",
                true,
                "Kwak"
        );
        User existingUser = User.builder()
                .id(10L)
                .email("kwakkun2002@gmail.com")
                .firebaseUid("existing-google-uid")
                .nickname("kwakkun")
                .build();
        UserAuthIdentity identity = UserAuthIdentity.builder()
                .id(22L)
                .user(existingUser)
                .firebaseUid("incoming-apple-uid")
                .providerId("apple.com")
                .email("kwakkun2002@gmail.com")
                .emailVerified(true)
                .build();

        when(userAuthIdentityRepository.findByFirebaseUid("incoming-apple-uid")).thenReturn(Optional.of(identity));

        User resolved = ReflectionTestUtils.invokeMethod(service, "resolveUser", token);

        assertSame(existingUser, resolved);
        verify(userRepository, never()).findByFirebaseUid("incoming-apple-uid");
    }

    @Test
    void verifiedEmailWithDifferentFirebaseUidResolvesExistingUser() {
        FirebaseToken token = firebaseToken(
                "incoming-apple-uid",
                "kwakkun2002@gmail.com",
                true,
                "Kwak"
        );
        User existingUser = User.builder()
                .id(10L)
                .email("kwakkun2002@gmail.com")
                .firebaseUid("existing-google-uid")
                .nickname("kwakkun")
                .build();

        when(userRepository.findByFirebaseUid("incoming-apple-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("kwakkun2002@gmail.com")).thenReturn(Optional.of(existingUser));

        User resolved = ReflectionTestUtils.invokeMethod(service, "resolveUser", token);

        assertSame(existingUser, resolved);
        assertEquals("existing-google-uid", existingUser.getFirebaseUid());
        verify(userAuthIdentityRepository).insertIgnore(
                10L,
                "incoming-apple-uid",
                "apple.com",
                "kwakkun2002@gmail.com",
                true
        );
        verify(userRepository, never()).save(existingUser);
    }

    @Test
    void sameProviderDifferentFirebaseUidResetsExistingUserState() {
        FirebaseToken token = firebaseToken(
                "incoming-google-uid",
                "kwakkun2002@gmail.com",
                true,
                "Kwak",
                "google.com"
        );
        User existingUser = User.builder()
                .id(10L)
                .email("kwakkun2002@gmail.com")
                .firebaseUid("old-google-uid")
                .username("firebase:old-google-uid")
                .password("old-password")
                .nickname("old-nickname")
                .profileImageUrl("https://example.com/old.png")
                .totalAssets(java.math.BigDecimal.TEN)
                .investmentAmount(java.math.BigDecimal.TEN)
                .profitLoss(java.math.BigDecimal.ONE)
                .profitLossRate(java.math.BigDecimal.ONE)
                .teamId("old-team")
                .role("admin")
                .investmentProfileResult("old-result")
                .investmentLevel("old-level")
                .interestSector("old-sector")
                .build();
        UserAuthIdentity identity = UserAuthIdentity.builder()
                .id(23L)
                .user(existingUser)
                .firebaseUid("incoming-google-uid")
                .providerId("google.com")
                .email("kwakkun2002@gmail.com")
                .emailVerified(true)
                .build();

        when(userAuthIdentityRepository.findByFirebaseUid("incoming-google-uid"))
                .thenReturn(Optional.of(identity), Optional.empty());
        when(userAuthIdentityRepository.existsOtherIdentityForUserAndProvider(
                10L,
                "google.com",
                "incoming-google-uid"
        )).thenReturn(true);
        when(userRepository.findByUsername("firebase:incoming-google-uid")).thenReturn(Optional.empty());
        when(userRepository.findByNickname("Kwak")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-new-password");
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User resolved = ReflectionTestUtils.invokeMethod(service, "resolveUser", token);

        assertSame(existingUser, resolved);
        assertEquals("incoming-google-uid", existingUser.getFirebaseUid());
        assertEquals("firebase:incoming-google-uid", existingUser.getUsername());
        assertEquals("encoded-new-password", existingUser.getPassword());
        assertEquals("Kwak", existingUser.getNickname());
        assertEquals(null, existingUser.getProfileImageUrl());
        assertEquals(null, existingUser.getInvestmentProfileResult());
        assertEquals(null, existingUser.getInvestmentLevel());
        assertEquals(null, existingUser.getInterestSector());
        assertEquals(null, existingUser.getTeamId());
        assertEquals("user", existingUser.getRole());
        assertEquals(new java.math.BigDecimal("10000000"), existingUser.getTotalAssets());
        assertEquals(new java.math.BigDecimal("10000000"), existingUser.getInvestmentAmount());
        assertEquals(java.math.BigDecimal.ZERO, existingUser.getProfitLoss());
        assertEquals(java.math.BigDecimal.ZERO, existingUser.getProfitLossRate());
        verify(userDeletionReferenceCleanupService).cleanupUserReferences(10L);
        verify(userAuthIdentityRepository).insertIgnore(
                10L,
                "incoming-google-uid",
                "google.com",
                "kwakkun2002@gmail.com",
                true
        );
    }

    @Test
    void unverifiedEmailWithDifferentFirebaseUidIsRejected() {
        FirebaseToken token = firebaseToken(
                "incoming-apple-uid",
                "kwakkun2002@gmail.com",
                false,
                "Kwak"
        );
        User existingUser = User.builder()
                .id(10L)
                .email("kwakkun2002@gmail.com")
                .firebaseUid("existing-google-uid")
                .nickname("kwakkun")
                .build();

        when(userRepository.findByFirebaseUid("incoming-apple-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("kwakkun2002@gmail.com")).thenReturn(Optional.of(existingUser));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "resolveUser", token)
        );

        assertEquals("FIREBASE_EMAIL_ALREADY_LINKED", exception.getErrorCode());
        verify(userAuthIdentityRepository, never()).insertIgnore(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
    }

    private FirebaseToken firebaseToken(String uid, String email, boolean emailVerified, String name) {
        return firebaseToken(uid, email, emailVerified, name, "apple.com");
    }

    private FirebaseToken firebaseToken(
            String uid,
            String email,
            boolean emailVerified,
            String name,
            String providerId) {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.isEmailVerified()).thenReturn(emailVerified);
        when(token.getName()).thenReturn(name);
        when(token.getClaims()).thenReturn(java.util.Map.of(
                "firebase",
                java.util.Map.of("sign_in_provider", providerId)
        ));
        return token;
    }
}
