package com.uniport.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppAdsTxtController {

    public static final String APP_ADS_TXT = "google.com, pub-3076485820966201, DIRECT, f08c47fec0942fa0";

    @GetMapping(value = "/app-ads.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAppAdsTxt() {
        return ResponseEntity.ok(APP_ADS_TXT + "\n");
    }
}
