package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "beta_ios_applications",
        indexes = {
                @Index(name = "idx_beta_ios_applications_apple_id_email", columnList = "apple_id_email", unique = true),
                @Index(name = "idx_beta_ios_applications_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class BetaIosApplication extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "apple_id_email", nullable = false, unique = true, length = 254)
    private String appleIdEmail;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(nullable = false, length = 40)
    private String device;

    @Column(nullable = false)
    private boolean consent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BetaIosApplicationStatus status;

    @Column(name = "app_store_connect_invitation_id", length = 120)
    private String appStoreConnectInvitationId;

    @Column(name = "invite_failure_message", length = 1000)
    private String inviteFailureMessage;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "beta_tester_id", length = 120)
    private String betaTesterId;

    @Column(name = "testflight_group_id", length = 120)
    private String testflightGroupId;

    @Column(name = "testflight_group_failure_message", length = 1000)
    private String testflightGroupFailureMessage;

    @Column(name = "testflight_group_added_at")
    private LocalDateTime testflightGroupAddedAt;
}
