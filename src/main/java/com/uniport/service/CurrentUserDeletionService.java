package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserDeletionService {

    private final UserRepository userRepository;
    private final UserDeletionReferenceCleanupService cleanupService;
    private final FirebaseAuthenticationService firebaseAuthenticationService;

    public CurrentUserDeletionService(
            UserRepository userRepository,
            UserDeletionReferenceCleanupService cleanupService,
            FirebaseAuthenticationService firebaseAuthenticationService) {
        this.userRepository = userRepository;
        this.cleanupService = cleanupService;
        this.firebaseAuthenticationService = firebaseAuthenticationService;
    }

    @Transactional
    public void deleteCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }

        Long userId = user.getId();
        String firebaseUid = user.getFirebaseUid();
        cleanupService.cleanupUserReferences(userId);
        userRepository.delete(user);
        if (firebaseUid != null && !firebaseUid.isBlank()) {
            firebaseAuthenticationService.deleteFirebaseUser(firebaseUid);
        }
    }
}
