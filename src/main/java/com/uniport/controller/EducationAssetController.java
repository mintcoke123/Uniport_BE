package com.uniport.controller;

import com.uniport.service.EducationAssetRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;
import java.time.Duration;

@RestController
public class EducationAssetController {

    private final EducationAssetRedirectService redirectService;

    public EducationAssetController(EducationAssetRedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/education-assets/**")
    public ResponseEntity<Void> redirectToBucketAsset(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        URI redirectUri = redirectService.createRedirectUri(request.getMethod(), path);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .build();
    }
}
