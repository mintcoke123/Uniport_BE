package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsFeedItemDTO {

    private Long id;
    private String title;
    private String summary;
    private String imageUrl;
    private String publishedAt;
    private String source;
}
