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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "asset_price_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_asset_price_daily_asset_date", columnNames = {"asset_id", "trade_date"}),
        indexes = {
                @Index(name = "idx_asset_price_daily_lookup", columnList = "asset_id, trade_date"),
                @Index(name = "idx_asset_price_daily_date", columnList = "trade_date")
        }
)
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class AssetPriceDaily extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 80)
    private String assetId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_krw", nullable = false, precision = 20, scale = 6)
    private BigDecimal closeKrw;

    @Column(name = "close_native", nullable = false, precision = 20, scale = 6)
    private BigDecimal closeNative;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 60)
    private String source;
}
