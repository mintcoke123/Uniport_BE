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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponseDTO registerUser(RegisterRequestDTO dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new ApiException("Email is required", HttpStatus.BAD_REQUEST);
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new ApiException("Password is required", HttpStatus.BAD_REQUEST);
        }
        if (dto.getNickname() == null || dto.getNickname().isBlank()) {
            throw new ApiException("Nickname is required", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmail(dto.getEmail().trim())) {
            throw new ApiException("Email already exists", HttpStatus.CONFLICT);
        }

        String email = dto.getEmail().trim();
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        User user = User.builder()
                .email(email)
                .username(email)
                .password(encodedPassword)
                .nickname(dto.getNickname().trim())
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
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new ApiException("Email is required", HttpStatus.BAD_REQUEST);
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new ApiException("Password is required", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmail(dto.getEmail().trim())
                .orElseThrow(() -> new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED);
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
        if (token == null || token.isBlank()) {
            throw new ApiException("Authorization token is required", HttpStatus.UNAUTHORIZED);
        }
        String bearer = "Bearer ";
        if (!token.startsWith(bearer)) {
            throw new ApiException("Invalid authorization header", HttpStatus.UNAUTHORIZED);
        }
        String jwt = token.substring(bearer.length()).trim();
        Long userId = jwtUtil.getUserIdFromToken(jwt);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.UNAUTHORIZED));
    }

    /** 토큰이 없거나 잘못되면 null 반환. 401 던지지 않음. (홈 등 미로그인 상태에서 호출 시 사용) */
    public User getUserFromTokenOrNull(String token) {
        if (token == null || token.isBlank()) return null;
        String bearer = "Bearer ";
        if (!token.startsWith(bearer)) return null;
        String jwt = token.substring(bearer.length()).trim();
        try {
            Long userId = jwtUtil.getUserIdFromToken(jwt);
            return userRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 토큰으로 현재 사용자 프로필 조회 (teamId 등 DB 최신값 반영). 미로그인 시 null. */
    public AuthUserDTO getCurrentUserDto(String authorization) {
        User u = getUserFromTokenOrNull(authorization != null ? authorization : "");
        return u != null ? toAuthUserDTO(u) : null;
    }

    private static AuthUserDTO toAuthUserDTO(User u) {
        return AuthUserDTO.builder()
                .id(u.getId() != null ? String.valueOf(u.getId()) : null)
                .email(u.getEmail())
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
