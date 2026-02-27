package com.uniport.service.importer;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * URL을 zip 파일로 다운로드. Java 표준 HTTPS 사용(SSL 우회 없음).
 */
@Component
public class DownloadClient {

    private final RestTemplate restTemplate;

    public DownloadClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * URL을 GET으로 받아 임시 zip 파일로 저장. 반환 경로는 호출자가 삭제해야 함.
     */
    public Path downloadToZipFile(String url) throws IOException {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
        );
        if (response.getBody() == null) {
            throw new IOException("Empty response body: " + url);
        }
        Path zipPath = Files.createTempFile("stock_master_", ".zip");
        Files.write(zipPath, response.getBody());
        return zipPath;
    }
}
