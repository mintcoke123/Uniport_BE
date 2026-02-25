package com.uniport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 투표 통과 조건 (agreeCount / totalMembers) > 0.5 단위 테스트.
 */
class VoteServiceTest {

    @Test
    void isVotePassedByRatio_1인_1찬성_통과() {
        assertTrue(VoteService.isVotePassedByRatio(1, 1));
    }

    @Test
    void isVotePassedByRatio_1인_0찬성_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(0, 1));
    }

    @Test
    void isVotePassedByRatio_2인_1찬성_0점5_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(1, 2));
    }

    @Test
    void isVotePassedByRatio_2인_2찬성_통과() {
        assertTrue(VoteService.isVotePassedByRatio(2, 2));
    }

    @Test
    void isVotePassedByRatio_3인_1찬성_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(1, 3));
    }

    @Test
    void isVotePassedByRatio_3인_2찬성_통과() {
        assertTrue(VoteService.isVotePassedByRatio(2, 3));
    }

    @Test
    void isVotePassedByRatio_totalMembers_0_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(0, 0));
        assertFalse(VoteService.isVotePassedByRatio(1, 0));
    }
}
