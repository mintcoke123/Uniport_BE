package com.uniport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Service
public class EducationAssetRedirectService {

    private static final DateTimeFormatter AMZ_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String SERVICE = "s3";
    private static final String TERMINATOR = "aws4_request";

    private final String endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;
    private final String region;
    private final String urlStyle;
    private final Duration expiresIn;
    private final Clock clock;

    @Autowired
    public EducationAssetRedirectService(
            @Value("${uniport.education.assets.bucket.endpoint:}") String endpoint,
            @Value("${uniport.education.assets.bucket.name:}") String bucket,
            @Value("${uniport.education.assets.bucket.access-key-id:}") String accessKeyId,
            @Value("${uniport.education.assets.bucket.secret-access-key:}") String secretAccessKey,
            @Value("${uniport.education.assets.bucket.session-token:}") String sessionToken,
            @Value("${uniport.education.assets.bucket.region:auto}") String region,
            @Value("${uniport.education.assets.bucket.url-style:virtual-host}") String urlStyle,
            @Value("${uniport.education.assets.bucket.presign-ttl-seconds:3600}") long expiresInSeconds) {
        this(endpoint, bucket, accessKeyId, secretAccessKey, sessionToken, region, urlStyle,
                Duration.ofSeconds(expiresInSeconds), Clock.systemUTC());
    }

    EducationAssetRedirectService(String endpoint,
                                  String bucket,
                                  String accessKeyId,
                                  String secretAccessKey,
                                  String sessionToken,
                                  String region,
                                  String urlStyle,
                                  Duration expiresIn,
                                  Clock clock) {
        this.endpoint = stripTrailingSlash(endpoint);
        this.bucket = trimToEmpty(bucket);
        this.accessKeyId = trimToEmpty(accessKeyId);
        this.secretAccessKey = trimToEmpty(secretAccessKey);
        this.sessionToken = trimToEmpty(sessionToken);
        this.region = trimToDefault(region, "auto");
        this.urlStyle = trimToDefault(urlStyle, "virtual-host");
        this.expiresIn = expiresIn;
        this.clock = clock;
    }

    public URI createRedirectUri(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        ensureConfigured();

        URI endpointUri = URI.create(endpoint);
        String host = resolveHost(endpointUri);
        String canonicalUri = resolveCanonicalUri(normalizedObjectKey);
        Instant now = clock.instant();
        String amzDate = AMZ_DATE_FORMATTER.format(now);
        String dateStamp = amzDate.substring(0, 8);
        String credentialScope = String.join("/", dateStamp, region, SERVICE, TERMINATOR);

        Map<String, String> queryParams = new TreeMap<>();
        queryParams.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        queryParams.put("X-Amz-Credential", accessKeyId + "/" + credentialScope);
        queryParams.put("X-Amz-Date", amzDate);
        queryParams.put("X-Amz-Expires", String.valueOf(Math.max(1, expiresIn.toSeconds())));
        queryParams.put("X-Amz-SignedHeaders", "host");
        if (!sessionToken.isBlank()) {
            queryParams.put("X-Amz-Security-Token", sessionToken);
        }

        String canonicalQueryString = canonicalQueryString(queryParams);
        String canonicalRequest = String.join("\n",
                "GET",
                canonicalUri,
                canonicalQueryString,
                "host:" + host + "\n",
                "host",
                "UNSIGNED-PAYLOAD");
        String stringToSign = String.join("\n",
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest));
        byte[] signingKey = signingKey(secretAccessKey, dateStamp, region, SERVICE);
        String signature = hmacHex(signingKey, stringToSign);

        return URI.create(endpointUri.getScheme() + "://" + host + canonicalUri
                + "?" + canonicalQueryString + "&X-Amz-Signature=" + signature);
    }

    private String resolveHost(URI endpointUri) {
        if ("virtual-host".equals(urlStyle)) {
            return bucket + "." + endpointUri.getHost();
        }
        if ("path".equals(urlStyle)) {
            return endpointUri.getHost();
        }
        throw new IllegalStateException("Unsupported bucket URL style: " + urlStyle);
    }

    private String resolveCanonicalUri(String objectKey) {
        if ("virtual-host".equals(urlStyle)) {
            return "/" + encodePath(objectKey);
        }
        return "/" + encodePath(bucket) + "/" + encodePath(objectKey);
    }

    private void ensureConfigured() {
        if (endpoint.isBlank() || bucket.isBlank() || accessKeyId.isBlank() || secretAccessKey.isBlank()) {
            throw new IllegalStateException("Education asset bucket is not configured.");
        }
    }

    private static String normalizeObjectKey(String objectKey) {
        String value = trimToEmpty(objectKey);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isBlank() || value.contains("..") || value.contains("\\")) {
            throw new IllegalArgumentException("Invalid education asset key.");
        }
        return value;
    }

    private static String canonicalQueryString(Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder();
        queryParams.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(encodeQuery(key)).append('=').append(encodeQuery(value));
        });
        return builder.toString();
    }

    private static String encodePath(String value) {
        String[] segments = value.split("/", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(encodeQuery(segments[i]));
        }
        return builder.toString();
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash education asset request.", exception);
        }
    }

    private static byte[] signingKey(String secretKey, String dateStamp, String region, String service) {
        byte[] dateKey = hmacBytes(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] regionKey = hmacBytes(dateKey, region);
        byte[] serviceKey = hmacBytes(regionKey, service);
        return hmacBytes(serviceKey, TERMINATOR);
    }

    private static byte[] hmacBytes(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign education asset request.", exception);
        }
    }

    private static String hmacHex(byte[] key, String value) {
        return HexFormat.of().formatHex(hmacBytes(key, value));
    }

    private static String stripTrailingSlash(String value) {
        return trimToEmpty(value).replaceFirst("/+$", "");
    }

    private static String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
