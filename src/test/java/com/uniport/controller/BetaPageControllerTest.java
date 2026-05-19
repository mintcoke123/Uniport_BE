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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(classes = {
        BetaPageControllerTest.TestConfig.class,
        SecurityConfig.class,
        CorsConfig.class,
        FirebaseAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class BetaPageControllerTest {

    private static final String ANDROID_INVITE_URL = "https://appdistribution.firebase.google.com/testerapps/example/releases/invite";

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
    void betaPage_allowsAnonymousAccessAndExplainsAndroidAndIosFlows() throws Exception {
        mockMvc.perform(get("/beta"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport Beta Test")))
                .andExpect(content().string(containsString("Android 베타 설치하기")))
                .andExpect(content().string(containsString("iPhone 베타 신청하기")))
                .andExpect(content().string(containsString("Firebase App Distribution")))
                .andExpect(content().string(containsString("TestFlight 내부 테스트")))
                .andExpect(content().string(containsString("kwakkun2002@gmail.com")))
                .andExpect(content().string(containsString("data-android-link=\"/beta/android\"")))
                .andExpect(content().string(containsString("fetch(\"/api/beta/ios-applications\"")))
                .andExpect(content().string(containsString("담당자에게 신청 내용 메일 보내기")))
                .andExpect(content().string(containsString("sendIosApplicationMail")))
                .andExpect(content().string(containsString("mailto:kwakkun2002%40gmail.com")));
    }

    @Test
    void betaHtmlAlias_allowsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/beta.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport Beta Test")));
    }

    @Test
    void betaIosAlias_allowsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/beta/ios"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Apple ID 이메일")));
    }

    @Test
    void betaAndroid_redirectsToConfiguredFirebaseInviteLink() throws Exception {
        mockMvc.perform(get("/beta/android"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ANDROID_INVITE_URL));
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        BetaPageController betaPageController() {
            return new BetaPageController(ANDROID_INVITE_URL, "kwakkun2002@gmail.com");
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
