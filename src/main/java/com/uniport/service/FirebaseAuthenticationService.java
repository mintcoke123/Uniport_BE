package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.config.FirebaseProperties;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FirebaseAuthenticationService {

    private static final BigDecimal INITIAL_ASSETS = new BigDecimal("10000000");
    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseProperties firebaseProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private volatile FirebaseApp firebaseApp;

    public FirebaseAuthenticationService(FirebaseProperties firebaseProperties,
                                         UserRepository userRepository,
                                         PasswordEncoder passwordEncoder) {
        this.firebaseProperties = firebaseProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public FirebaseAuthenticatedUser authenticate(String idToken) {
        FirebaseToken firebaseToken = verifyToken(idToken);
        User user = resolveUser(firebaseToken);
        return new FirebaseAuthenticatedUser(user, firebaseToken.getUid(), firebaseToken.getEmail());
    }

    public void deleteFirebaseUser(String firebaseUid) {
        String uid = trim(firebaseUid);
        if (uid.isBlank()) {
            return;
        }

        try {
            FirebaseAuth.getInstance(getOrInitializeFirebaseApp()).deleteUser(uid);
            log.info("[firebase-auth] Firebase user deleted: uid={}", uid);
        } catch (FirebaseAuthException ex) {
            if (ex.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                log.info("[firebase-auth] Firebase user already absent: uid={}", uid);
                return;
            }
            log.warn("[firebase-auth] Firebase user deletion failed: uid={}, code={}", uid, ex.getAuthErrorCode());
            throw new ApiException("Firebase account deletion failed", HttpStatus.INTERNAL_SERVER_ERROR,
                    "FIREBASE_ACCOUNT_DELETE_FAILED");
        } catch (IOException ex) {
            throw new ApiException("Firebase credentials initialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private User resolveUser(FirebaseToken firebaseToken) {
        String uid = trim(firebaseToken.getUid());
        return userRepository.findByFirebaseUid(uid)
                .map(existing -> updateExistingUser(existing, firebaseToken))
                .orElseGet(() -> findOrLinkExistingUserByEmail(firebaseToken)
                        .map(existing -> linkExistingUser(existing, firebaseToken))
                        .orElseGet(() -> createInitialUser(firebaseToken)));
    }

    private java.util.Optional<User> findOrLinkExistingUserByEmail(FirebaseToken firebaseToken) {
        String email = trim(firebaseToken.getEmail());
        if (email.isBlank()) {
            return java.util.Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    private User linkExistingUser(User existingUser, FirebaseToken firebaseToken) {
        String uid = trim(firebaseToken.getUid());
        String existingUid = trim(existingUser.getFirebaseUid());

        if (!existingUid.isBlank() && !existingUid.equals(uid)) {
            log.warn("[firebase-auth] Email already linked to another Firebase UID: email={}, existingUid={}, incomingUid={}",
                    existingUser.getEmail(), existingUid, uid);
            throw new ApiException("Email already linked to another Firebase account", HttpStatus.CONFLICT,
                    "FIREBASE_EMAIL_ALREADY_LINKED");
        }

        if (existingUid.isBlank()) {
            existingUser.setFirebaseUid(uid);
            log.info("[firebase-auth] Linked existing user to Firebase UID: userId={}, email={}, uid={}",
                    existingUser.getId(), existingUser.getEmail(), uid);
        }

        return updateExistingUser(existingUser, firebaseToken);
    }

    private FirebaseToken verifyToken(String idToken) {
        Map<String, Object> rawClaims = decodeTokenClaims(idToken);
        try {
            FirebaseToken firebaseToken = FirebaseAuth.getInstance(getOrInitializeFirebaseApp()).verifyIdToken(idToken);
            validateProjectAlignment(firebaseToken, rawClaims);
            return firebaseToken;
        } catch (FirebaseAuthException ex) {
            log.warn("[firebase-auth] ID token verification failed: code={}, configuredProjectId={}, credentialProjectId={}, tokenAud={}, tokenIss={}, tokenSub={}",
                    ex.getAuthErrorCode(),
                    configuredProjectIdOrBlank(),
                    configuredCredentialProjectId(),
                    rawClaims.getOrDefault("aud", ""),
                    rawClaims.getOrDefault("iss", ""),
                    rawClaims.containsKey("sub"));
            throw new IllegalArgumentException(mapFirebaseAuthMessage(ex));
        } catch (IOException ex) {
            throw new ApiException("Firebase credentials initialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[firebase-auth] Unexpected ID token verification failure: configuredProjectId={}, credentialProjectId={}, tokenAud={}, tokenIss={}, tokenSubPresent={}, message={}",
                    configuredProjectIdOrBlank(),
                    configuredCredentialProjectId(),
                    rawClaims.getOrDefault("aud", ""),
                    rawClaims.getOrDefault("iss", ""),
                    rawClaims.containsKey("sub"),
                    ex.getMessage());
            throw new IllegalArgumentException("Invalid Firebase ID token");
        }
    }

    private synchronized FirebaseApp getOrInitializeFirebaseApp() throws IOException {
        if (firebaseApp != null) {
            return firebaseApp;
        }
        List<FirebaseApp> apps = FirebaseApp.getApps();
        if (!apps.isEmpty()) {
            firebaseApp = apps.get(0);
            return firebaseApp;
        }

        GoogleCredentials credentials = loadCredentials();
        FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
        String configuredProjectId = configuredProjectIdOrBlank();
        if (!configuredProjectId.isBlank()) {
            builder.setProjectId(configuredProjectId);
        }
        firebaseApp = FirebaseApp.initializeApp(builder.build());
        log.info("[firebase-auth] FirebaseApp initialized: configuredProjectId={}, credentialProjectId={}, credentialsSource={}",
                configuredProjectId,
                configuredCredentialProjectId(),
                describeCredentialSource());
        return firebaseApp;
    }

    private GoogleCredentials loadCredentials() throws IOException {
        String credentialsJson = trim(firebaseProperties.getCredentialsJson());
        if (!credentialsJson.isBlank()) {
            try (InputStream inputStream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        String credentialsPath = trim(firebaseProperties.getCredentialsPath());
        if (!credentialsPath.isBlank()) {
            try (InputStream inputStream = Files.newInputStream(Path.of(credentialsPath))) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        throw new ApiException("Firebase credentials are not configured", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private User updateExistingUser(User user, FirebaseToken firebaseToken) {
        boolean dirty = false;
        String email = trim(firebaseToken.getEmail());
        if (!email.isBlank() && !email.equals(user.getEmail())) {
            user.setEmail(email);
            dirty = true;
        }
        String nickname = buildNicknameCandidate(firebaseToken);
        if (!nickname.isBlank() && (user.getNickname() == null || user.getNickname().isBlank() || user.getNickname().startsWith("user_"))) {
            String uniqueNickname = ensureUniqueNickname(nickname, user.getId());
            if (!uniqueNickname.equals(user.getNickname())) {
                user.setNickname(uniqueNickname);
                dirty = true;
            }
        }
        return dirty ? userRepository.save(user) : user;
    }

    static boolean shouldReplaceProfileImageFromFirebase(String currentProfileImageUrl, String firebasePicture) {
        // Provider profile photos are intentionally ignored so login scopes do not need profile image consent.
        return false;
    }

    private User createInitialUser(FirebaseToken firebaseToken) {
        String uid = firebaseToken.getUid();
        String generatedStudentId = generateStudentId();
        String generatedNickname = ensureUniqueNickname(buildNicknameCandidate(firebaseToken), null);
        String username = ensureUniqueUsername("firebase:" + uid, null);

        User user = User.builder()
                .firebaseUid(uid)
                .email(blankToNull(trim(firebaseToken.getEmail())))
                .studentId(generatedStudentId)
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .nickname(generatedNickname)
                .profileImageUrl(null)
                .totalAssets(INITIAL_ASSETS)
                .investmentAmount(INITIAL_ASSETS)
                .profitLoss(BigDecimal.ZERO)
                .profitLossRate(BigDecimal.ZERO)
                .teamId(null)
                .role("user")
                .investmentProfileResult(null)
                .build();
        return userRepository.save(user);
    }

    private String generateStudentId() {
        String base = "fb" + Instant.now().toEpochMilli();
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByStudentId(candidate)) {
            String suffixText = String.valueOf(++suffix);
            int end = Math.max(0, 20 - suffixText.length());
            candidate = base.substring(0, Math.min(base.length(), end)) + suffixText;
        }
        return candidate;
    }

    private String ensureUniqueNickname(String candidate, Long currentUserId) {
        String base = candidate == null || candidate.isBlank() ? "user" : candidate;
        String sanitizedBase = base.length() > 100 ? base.substring(0, 100) : base;
        String resolved = sanitizedBase;
        int suffix = 0;
        while (true) {
            var existing = userRepository.findByNickname(resolved).orElse(null);
            if (existing == null || (currentUserId != null && currentUserId.equals(existing.getId()))) {
                return resolved;
            }
            suffix++;
            String suffixText = "_" + suffix;
            int end = Math.max(0, 100 - suffixText.length());
            resolved = sanitizedBase.substring(0, Math.min(sanitizedBase.length(), end)) + suffixText;
        }
    }

    private String ensureUniqueUsername(String candidate, Long currentUserId) {
        String base = candidate.length() > 255 ? candidate.substring(0, 255) : candidate;
        String resolved = base;
        int suffix = 0;
        while (true) {
            var existing = userRepository.findByUsername(resolved).orElse(null);
            if (existing == null || (currentUserId != null && currentUserId.equals(existing.getId()))) {
                return resolved;
            }
            suffix++;
            String suffixText = "_" + suffix;
            int end = Math.max(0, 255 - suffixText.length());
            resolved = base.substring(0, Math.min(base.length(), end)) + suffixText;
        }
    }

    private String buildNicknameCandidate(FirebaseToken firebaseToken) {
        String name = trim(firebaseToken.getName());
        if (!name.isBlank()) {
            return name;
        }
        String email = trim(firebaseToken.getEmail());
        if (!email.isBlank() && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        String uid = trim(firebaseToken.getUid());
        return uid.isBlank() ? "user" : "user_" + uid.substring(0, Math.min(uid.length(), 8));
    }

    private String mapFirebaseAuthMessage(FirebaseAuthException ex) {
        AuthErrorCode errorCode = ex.getAuthErrorCode();
        if (errorCode == AuthErrorCode.EXPIRED_ID_TOKEN) {
            return "Firebase ID token has expired";
        }
        if (errorCode == AuthErrorCode.REVOKED_ID_TOKEN) {
            return "Firebase ID token has been revoked";
        }
        if (errorCode == AuthErrorCode.INVALID_ID_TOKEN) {
            return "Invalid Firebase ID token";
        }
        return "Firebase token verification failed";
    }

    private void validateProjectAlignment(FirebaseToken firebaseToken, Map<String, Object> rawClaims) {
        String expectedProjectId = resolveExpectedProjectId();
        if (expectedProjectId.isBlank()) {
            return;
        }

        String actualIssuer = trim(firebaseToken.getIssuer());
        String actualAudience = trim(String.valueOf(rawClaims.getOrDefault("aud", "")));
        String expectedIssuer = "https://securetoken.google.com/" + expectedProjectId;

        if (!actualAudience.isBlank() && !expectedProjectId.equals(actualAudience)) {
            log.warn("[firebase-auth] Project mismatch: expectedProjectId={}, credentialProjectId={}, tokenAud={}, tokenIss={}",
                    expectedProjectId, configuredCredentialProjectId(), actualAudience, actualIssuer);
            throw new IllegalArgumentException("Firebase project mismatch");
        }

        if (!actualIssuer.isBlank() && !expectedIssuer.equals(actualIssuer)) {
            log.warn("[firebase-auth] Issuer mismatch: expectedIssuer={}, credentialProjectId={}, tokenAud={}, tokenIss={}",
                    expectedIssuer, configuredCredentialProjectId(), actualAudience, actualIssuer);
            throw new IllegalArgumentException("Firebase project mismatch");
        }

        String rawIssuer = trim(String.valueOf(rawClaims.getOrDefault("iss", "")));
        if (!rawIssuer.isBlank() && !expectedIssuer.equals(rawIssuer)) {
            log.warn("[firebase-auth] Raw claim mismatch: expectedProjectId={}, credentialProjectId={}, tokenAud={}, tokenIss={}",
                    expectedProjectId, configuredCredentialProjectId(), actualAudience, rawIssuer);
            throw new IllegalArgumentException("Firebase project mismatch");
        }
    }

    private String resolveExpectedProjectId() {
        String configuredProjectId = configuredProjectIdOrBlank();
        if (!configuredProjectId.isBlank()) {
            return configuredProjectId;
        }
        return configuredCredentialProjectId();
    }

    private String configuredProjectIdOrBlank() {
        return trim(firebaseProperties.getProjectId());
    }

    private String configuredCredentialProjectId() {
        try {
            return loadCredentialProjectId();
        } catch (Exception ex) {
            log.warn("[firebase-auth] Failed to inspect credential project id: {}", ex.getMessage());
            return "";
        }
    }

    private String loadCredentialProjectId() throws IOException {
        String credentialsJson = trim(firebaseProperties.getCredentialsJson());
        if (!credentialsJson.isBlank()) {
            return extractProjectIdFromJson(credentialsJson);
        }

        String credentialsPath = trim(firebaseProperties.getCredentialsPath());
        if (!credentialsPath.isBlank()) {
            return extractProjectIdFromJson(Files.readString(Path.of(credentialsPath), StandardCharsets.UTF_8));
        }

        return "";
    }

    private String extractProjectIdFromJson(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return "";
        }
        Map<String, Object> jsonMap = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        return trim(String.valueOf(jsonMap.getOrDefault("project_id", "")));
    }

    private String describeCredentialSource() {
        String credentialsJson = trim(firebaseProperties.getCredentialsJson());
        if (!credentialsJson.isBlank()) {
            return "credentials-json";
        }
        String credentialsPath = trim(firebaseProperties.getCredentialsPath());
        if (!credentialsPath.isBlank()) {
            return "credentials-path";
        }
        return "not-configured";
    }

    private Map<String, Object> decodeTokenClaims(String idToken) {
        try {
            String[] segments = idToken.split("\\.");
            if (segments.length < 2) {
                return Collections.emptyMap();
            }
            byte[] decodedPayload = Base64.getUrlDecoder().decode(segments[1]);
            return OBJECT_MAPPER.readValue(decodedPayload, new TypeReference<>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
