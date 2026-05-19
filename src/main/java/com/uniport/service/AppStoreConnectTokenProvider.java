package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Service
public class AppStoreConnectTokenProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AUDIENCE = "appstoreconnect-v1";

    private final String issuerId;
    private final String keyId;
    private final String privateKeyPem;
    private final Clock clock;

    @Autowired
    public AppStoreConnectTokenProvider(
            @Value("${app.beta.ios.app-store-connect.issuer-id:}") String issuerId,
            @Value("${app.beta.ios.app-store-connect.key-id:}") String keyId,
            @Value("${app.beta.ios.app-store-connect.private-key:}") String privateKeyPem
    ) {
        this(issuerId, keyId, privateKeyPem, Clock.systemUTC());
    }

    AppStoreConnectTokenProvider(String issuerId, String keyId, String privateKeyPem, Clock clock) {
        this.issuerId = trimToEmpty(issuerId);
        this.keyId = trimToEmpty(keyId);
        this.privateKeyPem = trimToEmpty(privateKeyPem);
        this.clock = clock;
    }

    public String createToken() {
        if (issuerId.isBlank() || keyId.isBlank() || privateKeyPem.isBlank()) {
            throw new IllegalStateException("App Store Connect API credentials are not configured.");
        }

        try {
            Instant now = clock.instant();
            String header = encodeJson(Map.of(
                    "alg", "ES256",
                    "kid", keyId,
                    "typ", "JWT"
            ));
            String payload = encodeJson(Map.of(
                    "iss", issuerId,
                    "iat", now.getEpochSecond(),
                    "exp", now.plusSeconds(20 * 60).getEpochSecond(),
                    "aud", AUDIENCE
            ));
            String signingInput = header + "." + payload;
            byte[] derSignature = sign(signingInput.getBytes(StandardCharsets.US_ASCII), parsePrivateKey(privateKeyPem));
            return signingInput + "." + base64Url(derToJoseSignature(derSignature));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create App Store Connect API token.", e);
        }
    }

    private static String encodeJson(Map<String, Object> value) throws JsonProcessingException {
        return base64Url(OBJECT_MAPPER.writeValueAsBytes(value));
    }

    private static byte[] sign(byte[] input, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(input);
        return signature.sign();
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static byte[] derToJoseSignature(byte[] derSignature) {
        int offset = 0;
        if (derSignature[offset++] != 0x30) {
            throw new IllegalArgumentException("Invalid ECDSA DER signature.");
        }
        int sequenceLength = derSignature[offset++] & 0xff;
        if (sequenceLength > 0x80) {
            int lengthBytes = sequenceLength & 0x7f;
            sequenceLength = 0;
            for (int i = 0; i < lengthBytes; i++) {
                sequenceLength = (sequenceLength << 8) + (derSignature[offset++] & 0xff);
            }
        }
        if (offset + sequenceLength != derSignature.length || derSignature[offset++] != 0x02) {
            throw new IllegalArgumentException("Invalid ECDSA DER signature.");
        }
        int rLength = derSignature[offset++] & 0xff;
        byte[] r = Arrays.copyOfRange(derSignature, offset, offset + rLength);
        offset += rLength;
        if (derSignature[offset++] != 0x02) {
            throw new IllegalArgumentException("Invalid ECDSA DER signature.");
        }
        int sLength = derSignature[offset++] & 0xff;
        byte[] s = Arrays.copyOfRange(derSignature, offset, offset + sLength);

        byte[] jose = new byte[64];
        copyUnsigned(new BigInteger(1, r).toByteArray(), jose, 0);
        copyUnsigned(new BigInteger(1, s).toByteArray(), jose, 32);
        return jose;
    }

    private static void copyUnsigned(byte[] value, byte[] target, int targetOffset) {
        int sourceOffset = Math.max(0, value.length - 32);
        int length = Math.min(32, value.length);
        System.arraycopy(value, sourceOffset, target, targetOffset + 32 - length, length);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
