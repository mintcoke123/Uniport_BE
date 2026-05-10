package com.uniport.service;

public final class LearningProgressPolicy {

    public static final int MAX_EXP = 300;

    private LearningProgressPolicy() {
    }

    public static Progress fromExp(int totalExp) {
        int safeTotalExp = Math.max(0, totalExp);
        return new Progress(
                safeTotalExp / MAX_EXP + 1,
                safeTotalExp % MAX_EXP,
                MAX_EXP,
                safeTotalExp
        );
    }

    public record Progress(
            int level,
            int currentExp,
            int maxExp,
            int totalExp
    ) {
    }
}
