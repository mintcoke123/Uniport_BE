package com.uniport.service;

import com.uniport.dto.FriendInviteAcceptResponseDTO;
import com.uniport.dto.FriendInviteCreateResponseDTO;
import com.uniport.dto.FriendInviteDetailResponseDTO;
import com.uniport.entity.FriendInvite;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendInviteRepository;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class FriendInviteService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 16;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final FriendInviteRepository friendInviteRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final UserRepository userRepository;
    private final String inviteBaseUrl;
    private final long expirationDays;
    private final PushNotificationService pushNotificationService;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public FriendInviteService(FriendInviteRepository friendInviteRepository,
                               FriendRelationRepository friendRelationRepository,
                               UserRepository userRepository,
                               @Value("${app.friend-invite.base-url:https://uniportbe-production.up.railway.app}") String inviteBaseUrl,
                               @Value("${app.friend-invite.expiration-days:7}") long expirationDays,
                               PushNotificationService pushNotificationService,
                               UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                               ProfileImageUrlService profileImageUrlService) {
        this.friendInviteRepository = friendInviteRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.userRepository = userRepository;
        this.inviteBaseUrl = trimTrailingSlash(inviteBaseUrl);
        this.expirationDays = expirationDays;
        this.pushNotificationService = pushNotificationService;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.profileImageUrlService = profileImageUrlService;
    }

    @Transactional
    public FriendInviteCreateResponseDTO createInvite(User user) {
        User inviter = getRequiredUser(user);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expirationDays);
        FriendInvite invite = friendInviteRepository.save(FriendInvite.builder()
                .inviterUser(inviter)
                .inviteCode(generateUniqueInviteCode())
                .status("ACTIVE")
                .expiresAt(expiresAt)
                .build());

        return FriendInviteCreateResponseDTO.builder()
                .inviteCode(invite.getInviteCode())
                .inviteUrl(buildInviteUrl(invite.getInviteCode()))
                .expiresAt(toUtcString(invite.getExpiresAt()))
                .build();
    }

    @Transactional
    public FriendInviteDetailResponseDTO getInviteDetail(String inviteCode) {
        FriendInvite invite = getRequiredInvite(inviteCode);
        ensureInviteUsable(invite);
        User inviter = invite.getInviterUser();

        return FriendInviteDetailResponseDTO.builder()
                .inviteCode(invite.getInviteCode())
                .inviterUserId("USER_" + inviter.getId())
                .inviterNickname(inviter.getNickname())
                .inviterProfileImageUrl(profileImageUrlService.resolveCharacterProfileImageUrl(
                        inviter,
                        userMyPagePreferenceRepository.findById(inviter.getId()).orElse(null)
                ))
                .status(invite.getStatus())
                .expiresAt(toUtcString(invite.getExpiresAt()))
                .build();
    }

    @Transactional
    public FriendInviteAcceptResponseDTO acceptInvite(User user, String inviteCode) {
        User accepter = getRequiredUser(user);
        FriendInvite invite = getRequiredInvite(inviteCode);
        ensureInviteUsable(invite);
        User inviter = invite.getInviterUser();

        if (inviter.getId().equals(accepter.getId())) {
            throw new ApiException("cannot accept your own invite", HttpStatus.BAD_REQUEST);
        }

        friendRelationRepository.findBetweenUsers(inviter.getId(), accepter.getId())
                .ifPresent(relation -> {
                    if ("ACCEPTED".equalsIgnoreCase(relation.getStatus())) {
                        throw new ApiException("already friends", HttpStatus.CONFLICT);
                    }
                    throw new ApiException("friend relation already exists", HttpStatus.CONFLICT);
                });

        friendRelationRepository.save(FriendRelation.builder()
                .requesterUser(inviter)
                .addresseeUser(accepter)
                .status("ACCEPTED")
                .build());

        invite.setStatus("ACCEPTED");
        invite.setAcceptedByUser(accepter);
        invite.setAcceptedAt(LocalDateTime.now());
        friendInviteRepository.save(invite);
        pushNotificationService.sendFriendInviteAccepted(invite.getInviteCode(), inviter, accepter);

        return FriendInviteAcceptResponseDTO.builder()
                .friendUserId("USER_" + inviter.getId())
                .status("ACCEPTED")
                .build();
    }

    private FriendInvite getRequiredInvite(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ApiException("inviteCode is required", HttpStatus.BAD_REQUEST);
        }
        return friendInviteRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ApiException("friend invite not found", HttpStatus.NOT_FOUND));
    }

    private User getRequiredUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("user not found", HttpStatus.NOT_FOUND);
        }
        return userRepository.findById(user.getId())
                .orElseThrow(() -> new ApiException("user not found", HttpStatus.NOT_FOUND));
    }

    private void ensureInviteUsable(FriendInvite invite) {
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus("EXPIRED");
            friendInviteRepository.save(invite);
            throw new ApiException("friend invite expired", HttpStatus.GONE);
        }
        if (!"ACTIVE".equalsIgnoreCase(invite.getStatus())) {
            HttpStatus status = isGoneStatus(invite.getStatus()) ? HttpStatus.GONE : HttpStatus.CONFLICT;
            throw new ApiException("friend invite is already processed", status);
        }
    }

    private boolean isGoneStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return "EXPIRED".equals(normalized) || "CANCELLED".equals(normalized);
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateInviteCode();
            if (!friendInviteRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new ApiException("could not generate friend invite code", HttpStatus.CONFLICT);
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int index = 0; index < INVITE_CODE_LENGTH; index++) {
            builder.append(INVITE_CODE_CHARS[RANDOM.nextInt(INVITE_CODE_CHARS.length)]);
        }
        return builder.toString();
    }

    private String buildInviteUrl(String inviteCode) {
        return inviteBaseUrl + "/friend-invite?inviteCode=" + inviteCode;
    }

    private String toUtcString(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC).toString();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://uniportbe-production.up.railway.app";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
