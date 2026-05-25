package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDeletionReferenceCleanupService cleanupService;

    @Mock
    private FirebaseAuthenticationService firebaseAuthenticationService;

    @InjectMocks
    private UserAccountDeletionService userAccountDeletionService;

    @Test
    void deleteUser_cleansReferencesBeforeDeletingUserAndFirebaseAccount() {
        User user = User.builder().id(12L).firebaseUid("firebase-uid-12").build();

        userAccountDeletionService.deleteUser(user);

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
    void deleteUser_skipsFirebaseDeleteWhenFirebaseUidIsBlank() {
        User user = User.builder().id(12L).firebaseUid(" ").build();

        userAccountDeletionService.deleteUser(user);

        verify(firebaseAuthenticationService, never()).deleteFirebaseUser(null);
        verify(firebaseAuthenticationService, never()).deleteFirebaseUser("");
        verify(firebaseAuthenticationService, never()).deleteFirebaseUser(" ");
    }

    @Test
    void deleteUserById_loadsUserBeforeDeletingAccount() {
        User user = User.builder().id(12L).firebaseUid("firebase-uid-12").build();
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));

        userAccountDeletionService.deleteUserById(12L);

        verify(userRepository).findById(12L);
        verify(cleanupService).cleanupUserReferences(12L);
        verify(userRepository).delete(user);
        verify(firebaseAuthenticationService).deleteFirebaseUser("firebase-uid-12");
    }
}
