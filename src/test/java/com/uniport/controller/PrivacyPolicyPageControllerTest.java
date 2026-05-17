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
                .andExpect(content().string(containsString("시행일: 2026년 5월 14일")))
                .andExpect(content().string(containsString("11. 문의")))
                .andExpect(content().string(containsString("kwakkun2002@gmail.com")))
                .andExpect(content().string(containsString("mailto:kwakkun2002@gmail.com")))
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
