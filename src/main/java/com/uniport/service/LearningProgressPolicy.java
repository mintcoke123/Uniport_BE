package com.uniport.service;

public final class LearningProgressPolicy {

    public static final int MAX_EXP = 300;
    public static final int MAX_LEVEL = 100;

    private LearningProgressPolicy() {
    }

    public static Progress fromExp(int totalExp) {
        int safeTotalExp = Math.max(0, totalExp);
        int uncappedLevel = safeTotalExp / MAX_EXP + 1;
        int level = Math.min(uncappedLevel, MAX_LEVEL);
        int currentExp = uncappedLevel >= MAX_LEVEL ? MAX_EXP : safeTotalExp % MAX_EXP;
        return new Progress(
                level,
                currentExp,
                MAX_EXP,
                safeTotalExp,
                MAX_LEVEL
        );
    }

    public record Progress(
            int level,
            int currentExp,
            int maxExp,
            int totalExp,
            int maxLevel
    ) {
    }
}
