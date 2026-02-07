package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 명세 §3-5: news[] */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItemDTO {

    private Long id;
    private String title;
    private String source;
    private String date;
    private String summary;
}
