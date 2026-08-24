package com.vextis.shared.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseTokenAuthenticationFilterTests {

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
    private final ObjectProvider<FirebaseAuth> provider = mock(ObjectProvider.class);
    private final FirebaseTokenAuthenticationFilter filter = new FirebaseTokenAuthenticationFilter(provider);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesGraphQlWithVerifiedFirebaseUid() throws Exception {
        HttpServletRequest request = request("Bearer signed-id-token");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        FirebaseToken token = mock(FirebaseToken.class);
        when(provider.getObject()).thenReturn(firebaseAuth);
        when(firebaseAuth.verifyIdToken("signed-id-token")).thenReturn(token);
        when(token.getUid()).thenReturn("firebase-user-123");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("firebase-user-123");
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesAnonymousGraphQlForSecurityChainToReject() throws Exception {
        HttpServletRequest request = request(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    private HttpServletRequest request(String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/graphql");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        return request;
    }
}
