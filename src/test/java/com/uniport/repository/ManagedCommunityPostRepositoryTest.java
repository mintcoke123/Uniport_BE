package com.uniport.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedCommunityPostRepositoryTest {

    @Test
    void searchQueryDoesNotUppercaseNullableParameters() throws NoSuchMethodException {
        Method search = ManagedCommunityPostRepository.class
                .getMethod("search", String.class, String.class, String.class);

        String query = search.getAnnotation(Query.class).value();

        assertThat(query)
                .doesNotContain("upper(:type)")
                .doesNotContain("upper(:stockCode)")
                .doesNotContain("upper(:sentiment)");
        assertThat(query)
                .contains("upper(p.type) = :type")
                .contains("upper(coalesce(p.stockCode, '')) = :stockCode")
                .contains("upper(coalesce(p.sentiment, '')) = :sentiment");
    }
}
