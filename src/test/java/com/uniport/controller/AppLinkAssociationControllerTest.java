package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticationFilter;
import com.uniport.config.JwtUtil;
import com.uniport.repository.UserRepository;
import com.uniport.service.FirebaseAuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppLinkAssociationControllerTest {

    @Mock
    private FirebaseAuthenticationService firebaseAuthenticationService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Test
    void servesAssetLinksWithoutAuthentication() throws Exception {
        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"package_name\": \"com.crazyenough.uniport\"")))
                .andExpect(content().string(containsString("1C:42:50:20:7F:3A:5B:62:01:97:83:D0:C6:65:81:CA:AC:41:35:20:B6:42:F1:72:F6:C2:E4:4F:C3:88:2E:EE")));
    }

    @Test
    void servesAppleAssociationForBothPathsEvenWhenAuthorizationHeaderIsPresent() throws Exception {
        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/.well-known/apple-app-site-association")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"appID\": \"LU9899WD2P.com.crazyenough.uniport\"")))
                .andExpect(content().string(containsString("\"/matching-room*\"")))
                .andExpect(content().string(containsString("\"/friend-invite*\"")));

        mockMvc.perform(get("/apple-app-site-association")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"appID\": \"LU9899WD2P.com.crazyenough.uniport\"")));

        verifyNoInteractions(firebaseAuthenticationService, jwtUtil, userRepository);
    }

    private MockMvc buildMockMvc() {
        FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(
                firebaseAuthenticationService,
                jwtUtil,
                userRepository
        );

        return MockMvcBuilders.standaloneSetup(new AppLinkAssociationController())
                .addFilters(filter)
                .build();
    }
}
