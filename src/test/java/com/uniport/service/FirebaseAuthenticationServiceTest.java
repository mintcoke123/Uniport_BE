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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseAuthenticationServiceTest {

    private final FirebaseProperties firebaseProperties = new FirebaseProperties();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAuthIdentityRepository userAuthIdentityRepository = mock(UserAuthIdentityRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final FirebaseAuthenticationService service = new FirebaseAuthenticationService(
            firebaseProperties,
            userRepository,
            userAuthIdentityRepository,
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
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.isEmailVerified()).thenReturn(emailVerified);
        when(token.getName()).thenReturn(name);
        when(token.getClaims()).thenReturn(java.util.Map.of(
                "firebase",
                java.util.Map.of("sign_in_provider", "apple.com")
        ));
        return token;
    }
}
