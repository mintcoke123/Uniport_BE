package com.uniport.service;

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
import java.util.List;
import java.util.UUID;

@Service
public class FirebaseAuthenticationService {

    private static final BigDecimal INITIAL_ASSETS = new BigDecimal("10000000");

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
        User user = userRepository.findByFirebaseUid(firebaseToken.getUid())
                .map(existing -> updateExistingUser(existing, firebaseToken))
                .orElseGet(() -> createInitialUser(firebaseToken));
        return new FirebaseAuthenticatedUser(user, firebaseToken.getUid(), firebaseToken.getEmail());
    }

    private FirebaseToken verifyToken(String idToken) {
        try {
            return FirebaseAuth.getInstance(getOrInitializeFirebaseApp()).verifyIdToken(idToken);
        } catch (FirebaseAuthException ex) {
            throw new IllegalArgumentException(mapFirebaseAuthMessage(ex));
        } catch (IOException ex) {
            throw new ApiException("Firebase credentials initialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
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
        if (firebaseProperties.getProjectId() != null && !firebaseProperties.getProjectId().isBlank()) {
            builder.setProjectId(firebaseProperties.getProjectId().trim());
        }
        firebaseApp = FirebaseApp.initializeApp(builder.build());
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
        String picture = extractPicture(firebaseToken);
        if (!picture.isBlank() && !picture.equals(user.getProfileImageUrl())) {
            user.setProfileImageUrl(picture);
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
                .profileImageUrl(blankToNull(extractPicture(firebaseToken)))
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

    private String extractPicture(FirebaseToken firebaseToken) {
        Object picture = firebaseToken.getClaims().get("picture");
        if (picture == null) {
            return "";
        }
        return trim(String.valueOf(picture));
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

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
