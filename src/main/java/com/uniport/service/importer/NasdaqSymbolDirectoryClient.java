package com.uniport.service.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NasdaqSymbolDirectoryClient {

    private final RestTemplate restTemplate;
    private final String nasdaqListedUrl;
    private final String otherListedUrl;

    public NasdaqSymbolDirectoryClient(RestTemplate restTemplate,
                                       @Value("${asset.master.us.import.nasdaq-listed-url:https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqlisted.txt}") String nasdaqListedUrl,
                                       @Value("${asset.master.us.import.other-listed-url:https://www.nasdaqtrader.com/dynamic/SymDir/otherlisted.txt}") String otherListedUrl) {
        this.restTemplate = restTemplate;
        this.nasdaqListedUrl = nasdaqListedUrl;
        this.otherListedUrl = otherListedUrl;
    }

    public String downloadNasdaqListed() {
        return download(nasdaqListedUrl);
    }

    public String downloadOtherListed() {
        return download(otherListedUrl);
    }

    private String download(String url) {
        String body = restTemplate.getForObject(url, String.class);
        return body != null ? body : "";
    }
}
