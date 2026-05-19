package com.uniport.service;

import com.uniport.dto.BetaIosApplicationRequestDTO;
import com.uniport.dto.BetaIosApplicationResponseDTO;
import com.uniport.entity.BetaIosApplication;
import com.uniport.entity.BetaIosApplicationStatus;
import com.uniport.exception.ApiException;
import com.uniport.repository.BetaIosApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class BetaIosApplicationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final BetaIosApplicationRepository repository;
    private final AppStoreConnectUserInvitationClient invitationClient;

    public BetaIosApplicationService(BetaIosApplicationRepository repository,
                                     AppStoreConnectUserInvitationClient invitationClient) {
        this.repository = repository;
        this.invitationClient = invitationClient;
    }

    @Transactional
    public BetaIosApplicationResponseDTO submit(BetaIosApplicationRequestDTO request) {
        if (request == null || !Boolean.TRUE.equals(request.getConsent())) {
            throw new ApiException("iOS TestFlight 초대를 위한 개인정보 수집 및 이용 동의가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String name = requireText(request.getName(), "name is required");
        String appleIdEmail = normalizeEmail(request.getAppleIdEmail(), "appleIdEmail is required");
        String contactEmail = normalizeOptionalEmail(request.getContactEmail(), appleIdEmail);
        String device = normalizeDevice(request.getDevice());

        BetaIosApplication application = repository.findByAppleIdEmail(appleIdEmail)
                .orElseGet(() -> BetaIosApplication.builder()
                        .appleIdEmail(appleIdEmail)
                        .status(BetaIosApplicationStatus.REQUESTED)
                        .build());
        application.setName(name);
        application.setContactEmail(contactEmail);
        application.setDevice(device);
        application.setConsent(true);
        application.setStatus(BetaIosApplicationStatus.REQUESTED);
        application.setInviteFailureMessage(null);
        repository.save(application);

        AppStoreConnectUserInvitationResult inviteResult = invitationClient.inviteUser(
                new AppStoreConnectUserInvitationRequest(name, appleIdEmail)
        );
        applyInviteResult(application, inviteResult);
        BetaIosApplication saved = repository.save(application);
        return toResponse(saved, inviteResult);
    }

    private void applyInviteResult(BetaIosApplication application, AppStoreConnectUserInvitationResult inviteResult) {
        if (inviteResult.sent()) {
            application.setStatus(BetaIosApplicationStatus.USER_INVITE_SENT);
            application.setAppStoreConnectInvitationId(inviteResult.invitationId());
            application.setInvitedAt(LocalDateTime.now());
            return;
        }

        if (inviteResult.skipped()) {
            application.setStatus(BetaIosApplicationStatus.USER_INVITE_SKIPPED);
        } else {
            application.setStatus(BetaIosApplicationStatus.USER_INVITE_FAILED);
        }
        application.setInviteFailureMessage(inviteResult.message());
    }

    private BetaIosApplicationResponseDTO toResponse(
            BetaIosApplication application,
            AppStoreConnectUserInvitationResult inviteResult
    ) {
        return BetaIosApplicationResponseDTO.builder()
                .id(application.getId())
                .name(application.getName())
                .appleIdEmail(application.getAppleIdEmail())
                .contactEmail(application.getContactEmail())
                .device(application.getDevice())
                .status(application.getStatus().name())
                .message(messageFor(application.getStatus(), inviteResult.message()))
                .build();
    }

    private String messageFor(BetaIosApplicationStatus status, String fallback) {
        return switch (status) {
            case USER_INVITE_SENT -> "Apple 초대 메일을 보냈습니다. 메일에서 App Store Connect 초대를 수락한 뒤 TestFlight를 확인하세요.";
            case USER_INVITE_SKIPPED -> "신청은 저장됐습니다. 서버의 App Store Connect 자동 초대 설정이 완료되면 초대를 보낼 수 있습니다.";
            case USER_INVITE_FAILED -> "신청은 저장됐지만 Apple 초대 자동 발송에 실패했습니다. 현장 담당자에게 문의해 주세요.";
            case REQUESTED -> fallback;
        };
    }

    private String requireText(String value, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(message, HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private String normalizeEmail(String value, String message) {
        String email = requireText(value, message).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException("valid email is required", HttpStatus.BAD_REQUEST);
        }
        return email;
    }

    private String normalizeOptionalEmail(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return normalizeEmail(value, "contactEmail is invalid");
    }

    private String normalizeDevice(String value) {
        String device = value == null ? "" : value.trim();
        if (device.equalsIgnoreCase("iPad")) {
            return "iPad";
        }
        return "iPhone";
    }
}
