package com.uniport.controller;

import com.uniport.exception.ApiException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
public class InvestmentTestPageController {

    private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");
    private static final String PAGE_RESOURCE = "classpath:/static/investment-test/index.html";

    private final ResourceLoader resourceLoader;

    public InvestmentTestPageController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping(value = {"/investment-test", "/investment-test/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> investmentTestPage() {
        Resource resource = resourceLoader.getResource(PAGE_RESOURCE);
        if (!resource.exists()) {
            throw new ApiException("investment test page is unavailable", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String html = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(TEXT_HTML_UTF8)
                    .body(html);
        } catch (IOException e) {
            throw new ApiException("investment test page is unavailable", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
