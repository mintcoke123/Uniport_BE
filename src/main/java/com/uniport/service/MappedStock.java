package com.uniport.service;

public record MappedStock(
        String name,
        String symbol,
        String market,
        String matchType
) {
}
