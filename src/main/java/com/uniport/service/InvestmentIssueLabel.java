package com.uniport.service;

public enum InvestmentIssueLabel {
    POSITIVE("positive", "호재"),
    NEGATIVE("negative", "악재"),
    NEUTRAL("neutral", "중립"),
    MIXED("mixed", "혼합");

    private final String apiValue;
    private final String labelText;

    InvestmentIssueLabel(String apiValue, String labelText) {
        this.apiValue = apiValue;
        this.labelText = labelText;
    }

    public String apiValue() {
        return apiValue;
    }

    public String labelText() {
        return labelText;
    }
}
