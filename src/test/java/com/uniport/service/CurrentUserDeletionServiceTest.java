package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CurrentUserDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDeletionReferenceCleanupService cleanupService;

    @Mock
    private FirebaseAuthenticationService firebaseAuthenticationService;

    @InjectMocks
    private CurrentUserDeletionService currentUserDeletionService;

    @Test
    void deleteCurrentUser_cleansReferencesBeforeDeletingUser() {
        User user = User.builder().id(12L).firebaseUid("firebase-uid-12").build();

        currentUserDeletionService.deleteCurrentUser(user);

        InOrder order = inOrder(
                cleanupService,
                userRepository,
                firebaseAuthenticationService
        );
        order.verify(cleanupService).cleanupUserReferences(12L);
        order.verify(userRepository).delete(user);
        order.verify(firebaseAuthenticationService).deleteFirebaseUser("firebase-uid-12");
    }

    @Test
    void deleteCurrentUser_skipsFirebaseDeleteWhenFirebaseUidIsBlank() {
        User user = User.builder().id(12L).firebaseUid(" ").build();

        currentUserDeletionService.deleteCurrentUser(user);

        verify(firebaseAuthenticationService, never()).deleteFirebaseUser(null);
        verify(firebaseAuthenticationService, never()).deleteFirebaseUser("");
        verify(firebaseAuthenticationService, never()).deleteFirebaseUser(" ");
    }
}
