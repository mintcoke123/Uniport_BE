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

import java.time.LocalDateTime;

@Entity
@Table(name = "managed_news_articles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ManagedNewsArticle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String newsKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String sourceLabel;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 20)
    private String stockCode;

    @Column(length = 120)
    private String stockName;

    @Column(length = 2000)
    private String summary;

    @Lob
    private String content;

    @Lob
    private String companyInfoJson;

    @Lob
    private String tagsJson;

    @Lob
    private String opinionsJson;

    @Column
    private LocalDateTime publishedAt;
}
