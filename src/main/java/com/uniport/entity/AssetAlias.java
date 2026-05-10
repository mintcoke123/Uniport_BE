package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "asset_alias",
        uniqueConstraints = @UniqueConstraint(name = "uk_asset_alias_asset_alias", columnNames = {"asset_id", "alias"}),
        indexes = {
                @Index(name = "idx_asset_alias_lookup", columnList = "alias"),
                @Index(name = "idx_asset_alias_asset", columnList = "asset_id")
        }
)
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class AssetAlias extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 80)
    private String assetId;

    @Column(nullable = false, length = 160)
    private String alias;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(nullable = false, length = 60)
    private String source;

    @Column(nullable = false)
    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id", insertable = false, updatable = false)
    private AssetMaster asset;
}
