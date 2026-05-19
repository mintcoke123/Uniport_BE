package com.uniport.service;

import com.uniport.dto.StockVisualDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StockSymbolLogoUrlResolver {

    public StockSymbolLogoUrlResolver(
            @Value("${app.public-base-url:https://uniportbe-production.up.railway.app}") String publicBaseUrl) {
    }

    public String resolve(String market, String symbol, StockVisualDTO visual) {
        return null;
    }
}
