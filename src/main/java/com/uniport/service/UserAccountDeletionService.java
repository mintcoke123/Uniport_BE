package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserAuthIdentityRepository;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserAccountDeletionService {

    private final UserRepository userRepository;
    private final UserDeletionReferenceCleanupService cleanupService;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final FirebaseAuthenticationService firebaseAuthenticationService;

    public UserAccountDeletionService(
            UserRepository userRepository,
            UserDeletionReferenceCleanupService cleanupService,
            UserAuthIdentityRepository userAuthIdentityRepository,
            FirebaseAuthenticationService firebaseAuthenticationService) {
        this.userRepository = userRepository;
        this.cleanupService = cleanupService;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
        this.firebaseAuthenticationService = firebaseAuthenticationService;
    }

    @Transactional
    public void deleteUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("User not found", HttpStatus.NOT_FOUND);
        }

        Long userId = user.getId();
        Set<String> firebaseUids = findFirebaseUids(user);
        cleanupService.cleanupUserReferences(userId);
        userRepository.delete(user);
        firebaseUids.forEach(firebaseAuthenticationService::deleteFirebaseUser);
    }

    @Transactional
    public void deleteUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        deleteUser(user);
    }

    private Set<String> findFirebaseUids(User user) {
        Set<String> firebaseUids = new LinkedHashSet<>();
        addFirebaseUid(firebaseUids, user.getFirebaseUid());
        userAuthIdentityRepository.findFirebaseUidsByUserId(user.getId())
                .forEach(uid -> addFirebaseUid(firebaseUids, uid));
        return firebaseUids;
    }

    private void addFirebaseUid(Set<String> firebaseUids, String firebaseUid) {
        if (firebaseUid != null && !firebaseUid.isBlank()) {
            firebaseUids.add(firebaseUid);
        }
    }
}
