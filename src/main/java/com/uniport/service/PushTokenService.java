package com.uniport.service;

import com.uniport.dto.PushTokenRegisterRequestDTO;
import com.uniport.dto.PushTokenResponseDTO;
import com.uniport.dto.PushTokenUnregisterRequestDTO;
import com.uniport.entity.PushPermissionStatus;
import com.uniport.entity.PushPlatform;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.entity.UserPushToken;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserPushTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PushTokenService {

    private final UserPushTokenRepository userPushTokenRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;

    public PushTokenService(UserPushTokenRepository userPushTokenRepository,
                            UserMyPagePreferenceRepository userMyPagePreferenceRepository) {
        this.userPushTokenRepository = userPushTokenRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
    }

    @Transactional
    public PushTokenResponseDTO registerToken(User user, PushTokenRegisterRequestDTO request) {
        requireUser(user);
        String tokenValue = normalizeToken(request != null ? request.getToken() : null);
        PushPlatform platform = PushPlatform.fromWireValue(request != null ? request.getPlatform() : null);
        PushPermissionStatus permissionStatus = PushPermissionStatus.fromWireValue(
                request != null ? request.getPermissionStatus() : null
        );
        Instant now = Instant.now();

        UserPushToken token = userPushTokenRepository.findByToken(tokenValue)
                .orElseGet(() -> UserPushToken.builder()
                        .token(tokenValue)
                        .build());
        token.setUser(user);
        token.setPlatform(platform);
        token.setPermissionStatus(permissionStatus);
        token.setActive(permissionStatus.allowsDelivery());
        token.setLastSeenAt(now);
        if (permissionStatus.allowsDelivery()) {
            token.setRevokedAt(null);
        }

        return toResponse(userPushTokenRepository.save(token));
    }

    @Transactional
    public void unregisterToken(User user, PushTokenUnregisterRequestDTO request) {
        requireUser(user);
        String tokenValue = normalizeToken(request != null ? request.getToken() : null);
        userPushTokenRepository.findByToken(tokenValue)
                .filter(token -> token.getUser() != null && user.getId().equals(token.getUser().getId()))
                .ifPresent(token -> {
                    token.setActive(false);
                    token.setRevokedAt(Instant.now());
                    userPushTokenRepository.save(token);
                });
    }

    @Transactional(readOnly = true)
    public List<UserPushToken> getDeliverableTokens(Long userId) {
        if (userId == null) {
            return List.of();
        }
        boolean pushEnabled = userMyPagePreferenceRepository.findById(userId)
                .map(UserMyPagePreference::getPushEnabled)
                .orElse(Boolean.TRUE);
        if (!pushEnabled) {
            return List.of();
        }
        return userPushTokenRepository.findByUser_IdAndActiveTrue(userId);
    }

    @Transactional
    public void markTokenInvalid(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return;
        }
        userPushTokenRepository.findByToken(tokenValue.trim())
                .ifPresent(token -> {
                    token.setActive(false);
                    token.setRevokedAt(Instant.now());
                    userPushTokenRepository.save(token);
                });
    }

    private void requireUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException("token is required", HttpStatus.BAD_REQUEST);
        }
        return token.trim();
    }

    private PushTokenResponseDTO toResponse(UserPushToken token) {
        return PushTokenResponseDTO.builder()
                .id(token.getId())
                .platform(token.getPlatform().getWireValue())
                .permissionStatus(token.getPermissionStatus().getWireValue())
                .active(token.isActive())
                .lastSeenAt(DateTimeFormatter.ISO_INSTANT.format(token.getLastSeenAt()))
                .build();
    }
}
