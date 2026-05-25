package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CurrentUserDeletionServiceTest {

    @Mock
    private UserAccountDeletionService userAccountDeletionService;

    @InjectMocks
    private CurrentUserDeletionService currentUserDeletionService;

    @Test
    void deleteCurrentUser_delegatesToAccountDeletion() {
        User user = User.builder().id(12L).firebaseUid("firebase-uid-12").build();

        currentUserDeletionService.deleteCurrentUser(user);

        verify(userAccountDeletionService).deleteUser(user);
    }

    @Test
    void deleteCurrentUser_rejectsMissingUserBeforeDelegating() {
        User user = User.builder().firebaseUid("firebase-uid-12").build();

        assertThrows(ApiException.class, () -> currentUserDeletionService.deleteCurrentUser(user));

        verify(userAccountDeletionService, never()).deleteUser(user);
    }
}
