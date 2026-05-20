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
import org.springframework.core.io.DefaultResourceLoader;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(classes = {
        InvestmentTestPageControllerTest.TestConfig.class,
        SecurityConfig.class,
        CorsConfig.class,
        FirebaseAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class InvestmentTestPageControllerTest {

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
    void investmentTestPage_allowsAnonymousAccessAndServesMobileShell() throws Exception {
        mockMvc.perform(get("/investment-test"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 투자성향 테스트")))
                .andExpect(content().string(containsString("Design MCP")))
                .andExpect(content().string(containsString("진단 테스트를")))
                .andExpect(content().string(containsString("/investment-test/styles.css")))
                .andExpect(content().string(containsString("/investment-test/app.js")));
    }

    @Test
    void investmentTestTrailingSlash_allowsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/investment-test/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Uniport 투자성향 테스트")));
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        InvestmentTestPageController investmentTestPageController() {
            return new InvestmentTestPageController(new DefaultResourceLoader());
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
