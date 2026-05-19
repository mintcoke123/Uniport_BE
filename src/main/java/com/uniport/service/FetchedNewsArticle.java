package com.uniport.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedNewsArticle {

    private String id;
    private NewsCategory category;
    private String title;
    private String summary;
    private String content;
    private String sourceName;
    private LocalDateTime publishedAt;
    private boolean featured;
    private String externalUrl;
}
