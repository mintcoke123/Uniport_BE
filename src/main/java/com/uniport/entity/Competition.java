package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 모의투자 대회. 어드민에서 생성/수정하며, 진행 중 대회 종료일을 홈/대회 페이지에 제공.
 */
@Entity
@Table(name = "competitions")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Competition {

    public static final String MATCHING_STATUS_PENDING = "PENDING";
    public static final String MATCHING_STATUS_PROCESSING = "PROCESSING";
    public static final String MATCHING_STATUS_COMPLETED = "COMPLETED";
    public static final String MATCHING_STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /** ISO 날짜/시간 문자열 (예: 2025-03-01T00:00:00) */
    @Column(name = "start_date", nullable = false, length = 50)
    private String startDate;

    /** ISO 날짜/시간 문자열 (예: 2025-03-31T23:59:59) */
    @Column(name = "end_date", nullable = false, length = 50)
    private String endDate;

    /** ongoing | upcoming | ended */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "upcoming";

    @Column(name = "matching_status", length = 20)
    @Builder.Default
    private String matchingStatus = MATCHING_STATUS_PENDING;

    @Column(name = "matching_started_at")
    private Instant matchingStartedAt;

    @Column(name = "matching_completed_at")
    private Instant matchingCompletedAt;

    @Column(name = "matching_error_message", length = 1000)
    private String matchingErrorMessage;

    @Column(name = "start_notification_sent_at")
    private Instant startNotificationSentAt;
}
