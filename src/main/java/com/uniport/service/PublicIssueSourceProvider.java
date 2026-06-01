package com.uniport.service;

import java.util.List;

public interface PublicIssueSourceProvider {

    List<FetchedNewsArticle> fetchLatest();
}
