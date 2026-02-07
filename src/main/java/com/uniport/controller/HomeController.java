package com.uniport.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 루트(/) 접속 시 API 안내 메시지 반환. 403 대신 사용자 친화적 응답.
 */
@RestController
public class HomeController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "message", "Uniport API",
                "docs", "API는 /api/* 경로를 사용합니다. 예: POST /api/auth/login, GET /api/market/indices"
        ));
    }
}
