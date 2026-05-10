package com.uniport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningProgressPolicyTest {

    @Test
    void calculatesLevelAndCurrentExpFromTotalExp() {
        LearningProgressPolicy.Progress progress = LearningProgressPolicy.fromExp(500);

        assertEquals(2, progress.level());
        assertEquals(200, progress.currentExp());
        assertEquals(300, progress.maxExp());
    }

    @Test
    void levelStartsAtOneBeforeAnyExpIsEarned() {
        LearningProgressPolicy.Progress progress = LearningProgressPolicy.fromExp(0);

        assertEquals(1, progress.level());
        assertEquals(0, progress.currentExp());
        assertEquals(300, progress.maxExp());
    }
}
