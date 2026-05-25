package com.uniport.controller;

import com.uniport.config.CorsConfig;
import com.uniport.config.FirebaseAuthenticationFilter;
import com.uniport.config.JwtUtil;
import com.uniport.config.RestAccessDeniedHandler;
import com.uniport.config.RestAuthenticationEntryPoint;
import com.uniport.config.SecurityConfig;
import com.uniport.repository.UserRepository;
import com.uniport.service.FirebaseAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(classes = {
        PrivacyPolicyPageControllerTest.TestConfig.class,
        SecurityConfig.class,
        CorsConfig.class,
        FirebaseAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class PrivacyPolicyPageControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void privacyPolicyPage_allowsAnonymousAccessAndShowsPolicyContent() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 개인정보처리방침")))
                .andExpect(content().string(containsString("시행일: 2026년 5월 20일")))
                .andExpect(content().string(containsString("Google AdMob을 통해 앱 내 광고를 표시할 수 있습니다")))
                .andExpect(content().string(containsString("id=\"account-deletion\"")))
                .andExpect(content().string(containsString("계정 삭제 안내")))
                .andExpect(content().string(containsString("회원 탈퇴 또는 계정 삭제를 선택합니다")))
                .andExpect(content().string(containsString("11. 문의")))
                .andExpect(content().string(containsString("kwakkun2002@gmail.com")))
                .andExpect(content().string(containsString("mailto:kwakkun2002@gmail.com")))
                .andExpect(content().string(not(containsString("광고 SDK, 광고 식별자 기반 추적, 타사 광고 네트워크를 현재 사용하지 않습니다"))))
                .andExpect(content().string(not(containsString("KIS " + "Open API"))))
                .andExpect(content().string(not(containsString("Naver " + "News API"))));
    }

    @Test
    void privacyPolicyHtmlAlias_allowsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/privacy.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 개인정보처리방침")));
    }

    @Test
    void accountDeletionPage_allowsAnonymousAccessAndShowsDeletionRequestContent() throws Exception {
        mockMvc.perform(get("/account-deletion"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 계정 및 데이터 삭제 요청")))
                .andExpect(content().string(containsString("앱에서 계정 삭제하기")))
                .andExpect(content().string(containsString("이메일로 삭제 요청하기")))
                .andExpect(content().string(containsString("kwakkun2002@gmail.com")))
                .andExpect(content().string(containsString("삭제되는 데이터")))
                .andExpect(content().string(containsString("보관될 수 있는 데이터")))
                .andExpect(content().string(containsString("https://uniportbe-production.up.railway.app/privacy")));
    }

    @Test
    void accountDeletionAliases_allowAnonymousAccess() throws Exception {
        mockMvc.perform(get("/account-deletion.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 계정 및 데이터 삭제 요청")));

        mockMvc.perform(get("/delete-account"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 계정 및 데이터 삭제 요청")));

        mockMvc.perform(get("/delete-account.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 계정 및 데이터 삭제 요청")));
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        PrivacyPolicyPageController privacyPolicyPageController() {
            return new PrivacyPolicyPageController();
        }

        @Bean
        FirebaseAuthenticationService firebaseAuthenticationService() {
            return mock(FirebaseAuthenticationService.class);
        }

        @Bean
        JwtUtil jwtUtil() {
            return mock(JwtUtil.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}
