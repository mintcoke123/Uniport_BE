package com.uniport.service;

import java.util.List;

public interface NewsFeedClient {

    List<FetchedNewsArticle> fetchLatest();

    default String fetchArticleContent(String externalUrl) {
        return "";
    }
}
