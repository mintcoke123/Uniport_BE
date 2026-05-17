package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationQuizResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void educationQuizAnswersAreDistributedAcrossOptionPositions() throws IOException {
        List<Map<String, Object>> quizzes = readQuizzes();
        Set<Integer> answerIndexes = new LinkedHashSet<>();

        for (Map<String, Object> quiz : quizzes) {
            Integer answerIndex = (Integer) quiz.get("answerIndex");
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) quiz.get("options");

            assertTrue(
                    answerIndex != null && answerIndex >= 1 && answerIndex <= options.size(),
                    "answerIndex must point to an existing option: " + quiz.get("question"));
            answerIndexes.add(answerIndex);
        }

        assertFalse(quizzes.isEmpty());
        assertTrue(answerIndexes.size() > 1, "Quiz answers should not all be in the same option position.");
    }

    private List<Map<String, Object>> readQuizzes() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/education/education_quizzes.json")) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        }
    }
}
