package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PointSocialDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendsControllerTest {

    @Test
    void deleteFriend_returnsNoContentAndDelegatesToService() throws Exception {
        PointSocialDataService pointSocialDataService = mock(PointSocialDataService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder()
                .studentId("20262001")
                .password("password")
                .nickname("controller-friend-removal")
                .build();
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendsController(pointSocialDataService, currentUserResolver)).build();

        mockMvc.perform(delete("/api/friends/USER_99").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());

        verify(pointSocialDataService).deleteFriend(currentUser, "USER_99");
    }
}
