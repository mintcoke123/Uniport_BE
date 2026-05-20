package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "investment_test_reservations",
        indexes = {
                @Index(name = "idx_investment_test_reservations_contact", columnList = "contact_type, contact_value"),
                @Index(name = "idx_investment_test_reservations_result_key", columnList = "result_key")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_investment_test_reservations_contact",
                        columnNames = {"contact_type", "contact_value"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InvestmentTestReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "contact_type", nullable = false, length = 20)
    private String contactType;

    @Column(name = "contact_value", nullable = false, length = 254)
    private String contactValue;

    @Column(nullable = false)
    private boolean consent;

    @Column(name = "result_key", nullable = false, length = 40)
    private String resultKey;

    @Column(name = "result_title", nullable = false, length = 120)
    private String resultTitle;

    @Column(name = "interest_keywords_json", nullable = false, columnDefinition = "TEXT")
    private String interestKeywordsJson;

    @Column(name = "answers_json", nullable = false, columnDefinition = "TEXT")
    private String answersJson;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;
}
