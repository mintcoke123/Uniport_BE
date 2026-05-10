package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_master")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class AssetMaster extends AuditableEntity {

    @Id
    @Column(name = "asset_id", nullable = false, length = 80)
    private String assetId;

    @Column(name = "asset_type", nullable = false, length = 20)
    private String assetType;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(nullable = false, length = 30)
    private String market;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "backtest_enabled", nullable = false)
    private Boolean backtestEnabled;

    @Column(name = "price_source_status", nullable = false, length = 40)
    private String priceSourceStatus;

    @Column(name = "last_price_verified_at")
    private LocalDateTime lastPriceVerifiedAt;

    @Column(name = "last_price_error", length = 500)
    private String lastPriceError;
}
