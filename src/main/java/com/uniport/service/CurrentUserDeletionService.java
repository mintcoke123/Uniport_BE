package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserDeletionService {

    private final UserAccountDeletionService userAccountDeletionService;

    public CurrentUserDeletionService(UserAccountDeletionService userAccountDeletionService) {
        this.userAccountDeletionService = userAccountDeletionService;
    }

    @Transactional
    public void deleteCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }

        userAccountDeletionService.deleteUser(user);
    }
}
