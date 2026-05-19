package com.uniport.config;

import com.uniport.controller.RealtimeNewsController;
import com.uniport.controller.InvestmentIssueController;
import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.dto.RealtimeNewsListResponseDTO;
import com.uniport.repository.UserRepository;
import com.uniport.service.FirebaseAuthenticationService;
import com.uniport.service.InvestmentIssueService;
import com.uniport.service.NewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        RealtimeNewsSecurityTest.TestConfig.class,
        SecurityConfig.class,
        CorsConfig.class,
        FirebaseAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class RealtimeNewsSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NewsService newsService;

    @Autowired
    private InvestmentIssueService investmentIssueService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void realtimeNewsList_allowsAnonymousFrontendReads() throws Exception {
        when(newsService.getRealtimeNewsList("ALL", null, 1)).thenReturn(
                RealtimeNewsListResponseDTO.builder()
                        .items(List.of())
                        .hasNext(false)
                        .build()
        );

        mockMvc.perform(get("/api/mock-investing/realtime-news")
                        .param("category", "ALL")
                        .param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void investmentIssueList_allowsAnonymousFrontendReads() throws Exception {
        when(investmentIssueService.getIssueList("ALL", null, 1)).thenReturn(
                InvestmentIssueListResponseDTO.builder()
                        .items(List.of())
                        .hasNext(false)
                        .build()
        );

        mockMvc.perform(get("/api/mock-investing/investment-issues")
                        .param("category", "ALL")
                        .param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void staticAssets_allowAnonymousImageReads() throws Exception {
        mockMvc.perform(get("/assets/mypage/profile-options/dolphin.png"))
                .andExpect(status().isNotFound());
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        RealtimeNewsController realtimeNewsController(NewsService newsService) {
            return new RealtimeNewsController(newsService);
        }

        @Bean
        InvestmentIssueController investmentIssueController(InvestmentIssueService investmentIssueService) {
            return new InvestmentIssueController(investmentIssueService);
        }

        @Bean
        NewsService newsService() {
            return mock(NewsService.class);
        }

        @Bean
        InvestmentIssueService investmentIssueService() {
            return mock(InvestmentIssueService.class);
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
