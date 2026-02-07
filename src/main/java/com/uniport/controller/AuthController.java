package com.uniport.controller;

import com.uniport.dto.AuthResponseDTO;
import com.uniport.dto.LoginRequestDTO;
import com.uniport.dto.RegisterRequestDTO;
import com.uniport.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §1: 인증·사용자. 로그인(이메일·비밀번호), 회원가입.
 * 응답: { success, message, user } (로그인 시 token 포함).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody RegisterRequestDTO dto) {
        AuthResponseDTO response = authService.registerUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 프론트 연동: 회원가입 (AuthContext, 회원가입 페이지) — register와 동일 */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDTO> signup(@RequestBody RegisterRequestDTO dto) {
        AuthResponseDTO response = authService.registerUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO dto) {
        AuthResponseDTO response = authService.authenticateUser(dto);
        return ResponseEntity.ok(response);
    }
}
