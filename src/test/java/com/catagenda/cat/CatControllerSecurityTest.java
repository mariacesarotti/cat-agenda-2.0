package com.catagenda.cat;

import com.catagenda.spayneuter.SpayNeuterService;
import com.catagenda.user.CurrentUserProvider;
import com.catagenda.user.SecurityConfig;
import com.catagenda.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies T1.6's explicit acceptance criterion: a logged-in-only page
 * redirects unauthenticated requests to /login, and is reachable once
 * authenticated. @WebMvcTest loads only the web layer (no real database),
 * with SecurityConfig imported explicitly so the app's actual authorization
 * rules — not Spring Boot's slice-test defaults — are what's under test.
 */
@WebMvcTest(CatController.class)
@Import(SecurityConfig.class)
class CatControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatService catService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private SpayNeuterService spayNeuterService;

    @Test
    void unauthenticatedRequestToCatsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/cats"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    void authenticatedRequestToCatsSucceeds() throws Exception {
        when(currentUserProvider.get(any())).thenReturn(new User("owner@example.com", "hash"));
        when(catService.listForOwner(any())).thenReturn(List.of());

        mockMvc.perform(get("/cats"))
                .andExpect(status().isOk());
    }
}
