package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationAssetRedirectServiceTest {

    @Test
    void createsVirtualHostedPresignedUrlForEducationAsset() {
        EducationAssetRedirectService service = new EducationAssetRedirectService(
                "https://t3.storageapi.dev",
                "education-assets-abc123",
                "access",
                "secret",
                "",
                "auto",
                "virtual-host",
                Duration.ofSeconds(900),
                Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC));

        URI redirectUri = service.createRedirectUri("/education-assets/real_images/day 1.png");

        assertEquals("education-assets-abc123.t3.storageapi.dev", redirectUri.getHost());
        assertTrue(redirectUri.getRawPath().endsWith("/education-assets/real_images/day%201.png"));
        assertTrue(redirectUri.getRawQuery().contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
        assertTrue(redirectUri.getRawQuery().contains("X-Amz-Credential=access%2F20260511%2Fauto%2Fs3%2Faws4_request"));
        assertTrue(redirectUri.getRawQuery().contains("X-Amz-Expires=900"));
        assertTrue(redirectUri.getRawQuery().contains("X-Amz-Signature="));
    }

    @Test
    void createsMethodSpecificSignatureForHeadRequests() {
        EducationAssetRedirectService service = new EducationAssetRedirectService(
                "https://t3.storageapi.dev",
                "education-assets-abc123",
                "access",
                "secret",
                "",
                "auto",
                "virtual-host",
                Duration.ofSeconds(900),
                Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC));

        URI getRedirectUri = service.createRedirectUri("GET", "/education-assets/real_images/day 1.png");
        URI headRedirectUri = service.createRedirectUri("HEAD", "/education-assets/real_images/day 1.png");

        assertEquals(getRedirectUri.getScheme(), headRedirectUri.getScheme());
        assertEquals(getRedirectUri.getHost(), headRedirectUri.getHost());
        assertEquals(getRedirectUri.getRawPath(), headRedirectUri.getRawPath());
        assertTrue(getRedirectUri.getRawQuery().contains("X-Amz-Signature="));
        assertTrue(headRedirectUri.getRawQuery().contains("X-Amz-Signature="));
        assertTrue(!getRedirectUri.getRawQuery().equals(headRedirectUri.getRawQuery()));
    }

    @Test
    void rejectsTraversalLikeObjectKey() {
        EducationAssetRedirectService service = new EducationAssetRedirectService(
                "https://t3.storageapi.dev",
                "education-assets-abc123",
                "access",
                "secret",
                "",
                "auto",
                "virtual-host",
                Duration.ofSeconds(900),
                Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> service.createRedirectUri("/education-assets/../secret.png"));
    }
}
