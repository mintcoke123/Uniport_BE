package com.uniport.controller;

import com.uniport.service.EducationAssetRedirectService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EducationAssetControllerTest {

    @Test
    void signsHeadRedirectsWithHeadMethod() throws Exception {
        EducationAssetRedirectService redirectService = mock(EducationAssetRedirectService.class);
        when(redirectService.createRedirectUri("HEAD", "/education-assets/real_images/day.png"))
                .thenReturn(URI.create("https://bucket.example/education-assets/real_images/day.png?sig=head"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EducationAssetController(redirectService)).build();

        mockMvc.perform(head("/education-assets/real_images/day.png"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://bucket.example/education-assets/real_images/day.png?sig=head"));

        verify(redirectService).createRedirectUri("HEAD", "/education-assets/real_images/day.png");
    }
}
