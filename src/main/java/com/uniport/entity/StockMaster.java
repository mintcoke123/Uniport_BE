package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 종목 마스터. 스키마는 scripts/stock_master.sql 기준.
 * updated_at은 DB default now()로만 설정되며, 엔티티에서 덮어쓰지 않음.
 */
@Entity
@Table(name = "stock_master")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class StockMaster {

    @Id
    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Column(name = "std_code", length = 12)
    private String stdCode;

    @Column(name = "name_kr", nullable = false)
    private String nameKr;

    @Column(name = "market", nullable = false, length = 10)
    private String market;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
