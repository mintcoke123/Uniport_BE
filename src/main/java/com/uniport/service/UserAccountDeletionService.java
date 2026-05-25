package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountDeletionService {

    private final UserRepository userRepository;
    private final UserDeletionReferenceCleanupService cleanupService;
    private final FirebaseAuthenticationService firebaseAuthenticationService;

    public UserAccountDeletionService(
            UserRepository userRepository,
            UserDeletionReferenceCleanupService cleanupService,
            FirebaseAuthenticationService firebaseAuthenticationService) {
        this.userRepository = userRepository;
        this.cleanupService = cleanupService;
        this.firebaseAuthenticationService = firebaseAuthenticationService;
    }

    @Transactional
    public void deleteUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("User not found", HttpStatus.NOT_FOUND);
        }

        Long userId = user.getId();
        String firebaseUid = user.getFirebaseUid();
        cleanupService.cleanupUserReferences(userId);
        userRepository.delete(user);
        deleteFirebaseUserIfPresent(firebaseUid);
    }

    @Transactional
    public void deleteUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        deleteUser(user);
    }

    private void deleteFirebaseUserIfPresent(String firebaseUid) {
        if (firebaseUid != null && !firebaseUid.isBlank()) {
            firebaseAuthenticationService.deleteFirebaseUser(firebaseUid);
        }
    }
}
