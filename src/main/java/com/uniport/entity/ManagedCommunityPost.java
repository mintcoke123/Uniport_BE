package com.uniport.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "managed_community_posts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ManagedCommunityPost extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 120)
    private String authorName;

    @Column(length = 500)
    private String authorProfileImageUrl;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(length = 100)
    private String analysisReportId;

    private Integer likeCount;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ManagedCommunityComment> comments = new ArrayList<>();
}
