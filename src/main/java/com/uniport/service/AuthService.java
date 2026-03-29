package com.uniport.service;

import com.uniport.config.JwtUtil;
import com.uniport.dto.AuthResponseDTO;
import com.uniport.dto.AuthUserDTO;
import com.uniport.dto.LoginRequestDTO;
import com.uniport.dto.RegisterRequestDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 명세 §1: 이메일·비밀번호 로그인/회원가입. 응답은 success, message, user (및 로그인 시 token).
 */
@Service
public class AuthService {

    private static final BigDecimal INITIAL_ASSETS = new BigDecimal("10000000");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FirebaseAuthenticationService firebaseAuthenticationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       FirebaseAuthenticationService firebaseAuthenticationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.firebaseAuthenticationService = firebaseAuthenticationService;
    }

    @Transactional
    public AuthResponseDTO registerUser(RegisterRequestDTO dto) {
        if (dto.getStudentId() == null || dto.getStudentId().isBlank()) {
            throw new ApiException("학번을 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new ApiException("비밀번호를 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        if (dto.getNickname() == null || dto.getNickname().isBlank()) {
            throw new ApiException("닉네임을 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        String studentId = dto.getStudentId().trim();
        validateStudentId(studentId);
        if (userRepository.existsByStudentId(studentId)) {
            throw new ApiException("이미 사용 중인 학번입니다.", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByNickname(dto.getNickname().trim())) {
            throw new ApiException("이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT);
        }

        String phoneNumber = dto.getPhoneNumber() != null && !dto.getPhoneNumber().isBlank()
                ? dto.getPhoneNumber().trim() : null;
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        User user = User.builder()
                .studentId(studentId)
                .username(studentId)
                .password(encodedPassword)
                .nickname(dto.getNickname().trim())
                .phoneNumber(phoneNumber)
                .totalAssets(INITIAL_ASSETS)
                .investmentAmount(INITIAL_ASSETS)
                .profitLoss(BigDecimal.ZERO)
                .profitLossRate(BigDecimal.ZERO)
                .teamId(null)
                .role("user")
                .build();
        user = userRepository.save(user);

        String token = jwtUtil.createToken(user);
        AuthUserDTO userDto = toAuthUserDTO(user);
        return AuthResponseDTO.builder()
                .success(true)
                .message("Registration completed successfully.")
                .user(userDto)
                .token(token)
                .build();
    }

    public AuthResponseDTO authenticateUser(LoginRequestDTO dto) {
        if (dto.getStudentId() == null || dto.getStudentId().isBlank()) {
            throw new ApiException("Student ID is required", HttpStatus.BAD_REQUEST);
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new ApiException("Password is required", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByStudentId(dto.getStudentId().trim())
                .orElseThrow(() -> new ApiException("Invalid studentId or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ApiException("Invalid studentId or password", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtUtil.createToken(user);
        AuthUserDTO userDto = toAuthUserDTO(user);
        return AuthResponseDTO.builder()
                .success(true)
                .message("Login successful.")
                .user(userDto)
                .token(token)
                .build();
    }

    public User getUserFromToken(String token) {
        User user = resolveUserFromAuthorization(token, false);
        if (user == null) {
            throw new ApiException("User not found", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    /** 토큰이 없거나 잘못되면 null 반환. 401 던지지 않음. (홈 등 미로그인 상태에서 호출 시 사용) */
    public User getUserFromTokenOrNull(String token) {
        return resolveUserFromAuthorization(token, true);
    }

    /** 토큰으로 현재 사용자 프로필 조회 (teamId 등 DB 최신값 반영). 미로그인 시 null. */
    public AuthUserDTO getCurrentUserDto(String authorization) {
        User u = getUserFromTokenOrNull(authorization != null ? authorization : "");
        return u != null ? toAuthUserDTO(u) : null;
    }

    private User resolveUserFromAuthorization(String authorization, boolean silent) {
        if (authorization == null || authorization.isBlank()) {
            if (silent) return null;
            throw new ApiException("Authorization token is required", HttpStatus.UNAUTHORIZED);
        }
        String bearer = "Bearer ";
        if (!authorization.startsWith(bearer)) {
            if (silent) return null;
            throw new ApiException("Invalid authorization header", HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring(bearer.length()).trim();
        if (token.isBlank()) {
            if (silent) return null;
            throw new ApiException("Authorization token is required", HttpStatus.UNAUTHORIZED);
        }

        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            return userRepository.findById(userId).orElse(null);
        } catch (Exception ignored) {
        }

        try {
            return firebaseAuthenticationService.authenticate(token).getUser();
        } catch (ApiException e) {
            if (silent) return null;
            throw e;
        } catch (IllegalArgumentException e) {
            if (silent) return null;
            throw new ApiException(e.getMessage(), HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            if (silent) return null;
            throw new ApiException("Authentication failed", HttpStatus.UNAUTHORIZED);
        }
    }

    private static void validateStudentId(String studentId) {
        if (studentId == null || studentId.length() != 8) {
            throw new ApiException("학번은 숫자 8자리로 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        if (!studentId.matches("\\d+")) {
            throw new ApiException("학번은 숫자만 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        long value = Long.parseLong(studentId);
        if (value < 15000000L || value > 26999999L) {
            throw new ApiException("학번은 15000000~26999999 범위여야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static AuthUserDTO toAuthUserDTO(User u) {
        return AuthUserDTO.builder()
                .id(u.getId() != null ? String.valueOf(u.getId()) : null)
                .studentId(u.getStudentId())
                .nickname(u.getNickname())
                .totalAssets(u.getTotalAssets() != null ? u.getTotalAssets() : BigDecimal.ZERO)
                .investmentAmount(u.getInvestmentAmount() != null ? u.getInvestmentAmount() : BigDecimal.ZERO)
                .profitLoss(u.getProfitLoss() != null ? u.getProfitLoss() : BigDecimal.ZERO)
                .profitLossRate(u.getProfitLossRate() != null ? u.getProfitLossRate() : BigDecimal.ZERO)
                .teamId(u.getTeamId())
                .role(u.getRole() != null ? u.getRole() : "user")
                .build();
    }
}
