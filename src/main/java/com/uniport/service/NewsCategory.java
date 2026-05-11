package com.uniport.service;

public enum NewsCategory {
    ALL("전체"),
    MARKET("시황"),
    DOMESTIC_STOCK("국내주식"),
    OVERSEAS_STOCK("해외주식");

    private final String label;

    NewsCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
