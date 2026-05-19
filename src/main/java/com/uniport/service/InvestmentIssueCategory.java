package com.uniport.service;

public enum InvestmentIssueCategory {
    ALL("전체"),
    MARKET("시황"),
    THEME("테마"),
    COMPANY("종목"),
    OVERSEAS("해외");

    private final String label;

    InvestmentIssueCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
