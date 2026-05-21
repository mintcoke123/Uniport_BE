package com.uniport.config;

import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import com.uniport.service.FirebaseAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseAuthenticationFilterTest {

    private final FirebaseAuthenticationService firebaseAuthenticationService = mock(FirebaseAuthenticationService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(
            firebaseAuthenticationService,
            jwtUtil,
            userRepository
    );

    @Test
    void firebaseApiExceptionIsPreservedWhenJwtFallbackFails() {
        ApiException firebaseException = new ApiException(
                "Email already linked to another Firebase account",
                HttpStatus.CONFLICT,
                "FIREBASE_EMAIL_ALREADY_LINKED"
        );
        when(firebaseAuthenticationService.authenticate("firebase-token")).thenThrow(firebaseException);
        when(jwtUtil.getUserIdFromToken("firebase-token")).thenThrow(new ApiException("Invalid token", HttpStatus.UNAUTHORIZED));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> ReflectionTestUtils.invokeMethod(filter, "authenticatePrincipal", "firebase-token")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("FIREBASE_EMAIL_ALREADY_LINKED", exception.getErrorCode());
    }
}
