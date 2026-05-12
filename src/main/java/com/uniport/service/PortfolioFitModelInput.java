package com.uniport.service;

import java.util.List;

public record PortfolioFitModelInput(
        String portfolioLabel,
        List<String> portfolioKeywords,
        String candidateName,
        String candidateSymbol,
        String candidateMarket,
        List<String> candidateSignals
) {
}
