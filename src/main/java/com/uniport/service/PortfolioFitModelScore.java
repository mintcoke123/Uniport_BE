package com.uniport.service;

public record PortfolioFitModelScore(
        boolean positive,
        double confidence,
        String reason
) {
}
