package com.uniport.service;

import java.util.Optional;

public interface PortfolioFitModelClient {

    Optional<PortfolioFitModelScore> score(PortfolioFitModelInput input);
}
