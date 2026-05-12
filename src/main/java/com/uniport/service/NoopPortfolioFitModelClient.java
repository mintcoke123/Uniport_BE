package com.uniport.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class NoopPortfolioFitModelClient implements PortfolioFitModelClient {

    @Override
    public Optional<PortfolioFitModelScore> score(PortfolioFitModelInput input) {
        return Optional.empty();
    }
}
