package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "managed_etfs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ManagedEtf extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String etfCode;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 100)
    private String theme;

    @Column(length = 50)
    private String benchmark;

    @Column(length = 30)
    private String period;

    @Column(length = 30)
    private String riskLevel;

    @Column(precision = 10, scale = 4)
    private BigDecimal returnRate;

    private Integer popularityScore;

    private Integer favoriteCount;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 1000)
    private String shortDescription;

    @Lob
    private String holdingsJson;

    @Lob
    private String trendPointsJson;

    @Lob
    private String analysisSummaryJson;

    @Column
    private LocalDateTime publishedAt;
}
