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

@Entity
@Table(name = "managed_group_insights")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ManagedGroupInsight extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String insightKey;

    private Long topGroupId;

    @Column(length = 150)
    private String topGroupName;

    @Column(precision = 10, scale = 4)
    private BigDecimal dailyReturnRate;

    @Column(length = 30)
    private String topPick;

    @Column(length = 3000)
    private String comment;

    @Lob
    private String consensusJson;
}
