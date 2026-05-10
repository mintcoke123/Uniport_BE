package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "education_cards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_education_card_idx", columnNames = {"source_idx"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class EducationCardEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_idx", nullable = false)
    private Integer sourceIdx;

    @Column(length = 120, unique = true)
    private String assetId;

    @Column(length = 120)
    private String sheet;

    @Column(nullable = false, length = 40)
    private String track;

    @Column(length = 120)
    private String sector;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(length = 200)
    private String section;

    @Column(length = 20)
    private String cardNumber;

    @Column(length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(length = 40)
    private String imageType;

    @Column(length = 100)
    private String svgPreset;

    @Column(length = 40)
    private String templateType;

    @Column(length = 60)
    private String rendererType;

    @Column(length = 60)
    private String visualType;

    @Column(length = 160)
    private String visualKey;

    @Column(length = 160)
    private String componentKey;

    @Column(length = 160)
    private String assetKey;

    @Column(length = 40)
    private String imageDelivery;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String visualJson;

    @Column(columnDefinition = "TEXT")
    private String visualPayloadJson;

    @Column(columnDefinition = "TEXT")
    private String renderPolicyJson;
}
