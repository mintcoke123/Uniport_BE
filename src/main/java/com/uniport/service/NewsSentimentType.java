package com.uniport.service;

enum NewsSentimentType {
    POSITIVE("호재"),
    NEGATIVE("악재");

    private final String label;

    NewsSentimentType(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
